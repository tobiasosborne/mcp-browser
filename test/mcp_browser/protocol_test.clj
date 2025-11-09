(ns mcp-browser.protocol-test
  (:require [clojure.test :refer [deftest testing is]]
            [mcp-browser.protocol :as protocol]
            [cheshire.core :as json]))

;; ============================================================================
;; Response Creation Tests
;; ============================================================================

(deftest test-success-response
  (testing "Creates valid success response"
    (let [response (protocol/success-response 1 {:status "ok"})]
      (is (= "2.0" (:jsonrpc response)))
      (is (= 1 (:id response)))
      (is (= {:status "ok"} (:result response)))))
  
  (testing "Success response with string id"
    (let [response (protocol/success-response "test-123" {:data "test"})]
      (is (= "test-123" (:id response)))
      (is (= {:data "test"} (:result response))))))

(deftest test-error-response
  (testing "Creates valid error response"
    (let [response (protocol/error-response 1 -32600 "Invalid Request")]
      (is (= "2.0" (:jsonrpc response)))
      (is (= 1 (:id response)))
      (is (= -32600 (get-in response [:error :code])))
      (is (= "Invalid Request" (get-in response [:error :message])))))
  
  (testing "Error response with additional data"
    (let [response (protocol/error-response 2 -32603 "Internal error" {:details "test"})]
      (is (= {:details "test"} (get-in response [:error :data]))))))

;; ============================================================================
;; Request Validation Tests
;; ============================================================================

(deftest test-validate-request
  (testing "Valid request passes validation"
    (is (true? (protocol/validate-request
                {:jsonrpc "2.0"
                 :id 1
                 :method "test"}))))
  
  (testing "Request without jsonrpc fails"
    (is (false? (protocol/validate-request
                 {:id 1
                  :method "test"}))))
  
  (testing "Request with wrong jsonrpc version fails"
    (is (false? (protocol/validate-request
                 {:jsonrpc "1.0"
                  :id 1
                  :method "test"}))))
  
  (testing "Request without method fails"
    (is (false? (protocol/validate-request
                 {:jsonrpc "2.0"
                  :id 1}))))
  
  (testing "Request with string id is valid"
    (is (true? (protocol/validate-request
                {:jsonrpc "2.0"
                 :id "test-id"
                 :method "test"}))))
  
  (testing "Request without id is valid (notification)"
    (is (true? (protocol/validate-request
                {:jsonrpc "2.0"
                 :method "test"})))))

;; ============================================================================
;; Handler Tests
;; ============================================================================

(deftest test-handle-initialize
  (testing "Initialize returns correct structure"
    (let [result (protocol/handle-initialize {:clientInfo {:name "test"}})]
      (is (contains? result :protocolVersion))
      (is (contains? result :capabilities))
      (is (contains? result :serverInfo))
      (is (= "mcp-browser" (get-in result [:serverInfo :name])))))
  
  (testing "Initialize includes capabilities"
    (let [result (protocol/handle-initialize {})]
      (is (contains? (:capabilities result) :tools))
      (is (contains? (:capabilities result) :logging)))))

(deftest test-handle-list-tools
  (testing "Returns list of tools"
    (let [result (protocol/handle-list-tools)]
      (is (contains? result :tools))
      (is (vector? (:tools result)))
      (is (pos? (count (:tools result))))))
  
  (testing "Each tool has required fields"
    (let [tools (:tools (protocol/handle-list-tools))]
      (doseq [tool tools]
        (is (contains? tool :name))
        (is (contains? tool :description))
        (is (contains? tool :inputSchema))
        (is (string? (:name tool)))
        (is (string? (:description tool)))
        (is (map? (:inputSchema tool))))))
  
  (testing "browse_page tool exists"
    (let [tools (:tools (protocol/handle-list-tools))
          browse-tool (first (filter #(= "browse_page" (:name %)) tools))]
      (is (some? browse-tool))
      (is (= "object" (get-in browse-tool [:inputSchema :type])))
      (is (contains? (get-in browse-tool [:inputSchema :properties]) :url))))
  
  (testing "create_session tool exists"
    (let [tools (:tools (protocol/handle-list-tools))
          session-tool (first (filter #(= "create_session" (:name %)) tools))]
      (is (some? session-tool)))))

(deftest test-handle-call-tool
  (testing "Returns result for tool call"
    (let [result (protocol/handle-call-tool "browse_page" {:url "https://example.com"})]
      (is (some? result))
      ;; Now that tools are implemented, we get actual content
      (is (contains? result :content))
      (is (contains? result :isError))
      (is (false? (:isError result))))))

;; ============================================================================
;; Request Routing Tests
;; ============================================================================

(deftest test-route-initialize
  (testing "Routes initialize request correctly"
    (let [request {:jsonrpc "2.0"
                   :id 1
                   :method "initialize"
                   :params {:clientInfo {:name "test"}}}
          response (protocol/route-request request)]
      (is (= "2.0" (:jsonrpc response)))
      (is (= 1 (:id response)))
      (is (contains? (:result response) :protocolVersion))
      (is (nil? (:error response))))))

(deftest test-route-list-tools
  (testing "Routes tools/list request correctly"
    (let [request {:jsonrpc "2.0"
                   :id 2
                   :method "tools/list"
                   :params {}}
          response (protocol/route-request request)]
      (is (= 2 (:id response)))
      (is (contains? (:result response) :tools))
      (is (nil? (:error response))))))

(deftest test-route-call-tool
  (testing "Routes tools/call request correctly"
    (let [request {:jsonrpc "2.0"
                   :id 3
                   :method "tools/call"
                   :params {:name "browse_page"
                           :arguments {:url "https://example.com"}}}
          response (protocol/route-request request)]
      (is (= 3 (:id response)))
      (is (some? (:result response))))))

(deftest test-route-ping
  (testing "Routes ping request correctly"
    (let [request {:jsonrpc "2.0"
                   :id 4
                   :method "ping"}
          response (protocol/route-request request)]
      (is (= 4 (:id response)))
      (is (= {} (:result response))))))

(deftest test-route-unknown-method
  (testing "Returns error for unknown method"
    (let [request {:jsonrpc "2.0"
                   :id 5
                   :method "unknown/method"}
          response (protocol/route-request request)]
      (is (= 5 (:id response)))
      (is (some? (:error response)))
      (is (= -32601 (get-in response [:error :code]))))))

(deftest test-route-error-handling
  (testing "Returns error response on exception"
    (let [request {:jsonrpc "2.0"
                   :id 6
                   :method "initialize"
                   :params nil}]
      ;; This might throw if handler doesn't expect nil params
      (let [response (protocol/route-request request)]
        (is (= 6 (:id response)))
        ;; Should either succeed or return proper error
        (is (or (contains? response :result)
                (contains? response :error)))))))

;; ============================================================================
;; Message Processing Tests
;; ============================================================================

(deftest test-process-message
  (testing "Processes valid message"
    (let [message {:jsonrpc "2.0"
                   :id 1
                   :method "ping"}
          response (protocol/process-message message)]
      (is (some? response))
      (is (= 1 (:id response)))))
  
  (testing "Rejects message without jsonrpc field"
    (let [message {:id 1
                   :method "ping"}
          response (protocol/process-message message)]
      (is (some? (:error response)))
      (is (= -32600 (get-in response [:error :code])))))
  
  (testing "Rejects message with wrong jsonrpc version"
    (let [message {:jsonrpc "1.0"
                   :id 1
                   :method "ping"}
          response (protocol/process-message message)]
      (is (some? (:error response)))
      (is (= -32600 (get-in response [:error :code])))))
  
  (testing "Rejects message without method"
    (let [message {:jsonrpc "2.0"
                   :id 1}
          response (protocol/process-message message)]
      (is (some? (:error response)))
      (is (= -32600 (get-in response [:error :code])))))
  
  (testing "Returns nil for nil message"
    (is (nil? (protocol/process-message nil)))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest test-full-initialize-flow
  (testing "Complete initialize handshake"
    (let [init-request {:jsonrpc "2.0"
                        :id 1
                        :method "initialize"
                        :params {:protocolVersion "2024-11-05"
                                :capabilities {}
                                :clientInfo {:name "test-client"
                                           :version "1.0.0"}}}
          init-response (protocol/process-message init-request)]
      
      ;; Verify initialize response
      (is (= 1 (:id init-response)))
      (is (nil? (:error init-response)))
      (is (some? (get-in init-response [:result :protocolVersion])))
      (is (some? (get-in init-response [:result :capabilities])))
      
      ;; After initialize, list tools
      (let [list-request {:jsonrpc "2.0"
                          :id 2
                          :method "tools/list"}
            list-response (protocol/process-message list-request)]
        
        (is (= 2 (:id list-response)))
        (is (nil? (:error list-response)))
        (is (vector? (get-in list-response [:result :tools])))
        (is (pos? (count (get-in list-response [:result :tools]))))))))

(deftest test-tool-schema-validity
  (testing "All tool schemas are valid JSON Schema"
    (let [tools (:tools (protocol/handle-list-tools))]
      (doseq [tool tools]
        (let [schema (:inputSchema tool)]
          ;; Basic JSON Schema validation
          (is (= "object" (:type schema)))
          (is (map? (:properties schema)))
          (is (or (nil? (:required schema))
                  (vector? (:required schema)))))))))
