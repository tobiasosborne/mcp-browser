(ns mcp-browser.integration-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [mcp-browser.protocol :as protocol]
            [mcp-browser.browser-management :as browser]))

;; Test Fixtures
(defn cleanup-fixture
  [f]
  (try
    (f)
    (finally
      (browser/cleanup-all-sessions))))

(use-fixtures :each cleanup-fixture)

;; ============================================================================
;; End-to-End Protocol Tests
;; ============================================================================

(deftest test-full-mcp-handshake
  (testing "Complete MCP initialization and tool usage"
    ;; 1. Initialize
    (let [init-msg {:jsonrpc "2.0"
                    :id 1
                    :method "initialize"
                    :params {:protocolVersion "2024-11-05"
                            :capabilities {}
                            :clientInfo {:name "test-client" :version "1.0.0"}}}
          init-response (protocol/process-message init-msg)]
      
      (is (= 1 (:id init-response)))
      (is (nil? (:error init-response)))
      (is (some? (get-in init-response [:result :protocolVersion])))
      
      ;; 2. List tools
      (let [list-msg {:jsonrpc "2.0"
                      :id 2
                      :method "tools/list"}
            list-response (protocol/process-message list-msg)]
        
        (is (= 2 (:id list-response)))
        (is (nil? (:error list-response)))
        (is (pos? (count (get-in list-response [:result :tools]))))
        
        ;; 3. Call browse_page tool
        (let [browse-msg {:jsonrpc "2.0"
                          :id 3
                          :method "tools/call"
                          :params {:name "browse_page"
                                  :arguments {:url "https://example.com"}}}
              browse-response (protocol/process-message browse-msg)]
          
          (is (= 3 (:id browse-response)))
          (is (nil? (:error browse-response)))
          (is (false? (get-in browse-response [:result :isError])))
          (is (vector? (get-in browse-response [:result :content]))))))))

(deftest test-session-workflow-via-protocol
  (testing "Complete session workflow through MCP protocol"
    ;; 1. Create session
    (let [create-msg {:jsonrpc "2.0"
                      :id 1
                      :method "tools/call"
                      :params {:name "create_session"
                              :arguments {:browser "w3m"}}}
          create-response (protocol/process-message create-msg)
          session-id (get-in create-response [:result :session_id])]
      
      (is (some? session-id))
      (is (false? (get-in create-response [:result :isError])))
      
      ;; 2. Navigate in session
      (let [nav-msg {:jsonrpc "2.0"
                     :id 2
                     :method "tools/call"
                     :params {:name "navigate"
                             :arguments {:session_id session-id
                                        :url "https://example.com"}}}
            nav-response (protocol/process-message nav-msg)]
        
        (is (= 2 (:id nav-response)))
        (is (false? (get-in nav-response [:result :isError])))
        
        ;; 3. Get session info
        (let [info-msg {:jsonrpc "2.0"
                        :id 3
                        :method "tools/call"
                        :params {:name "get_session_info"
                                :arguments {:session_id session-id}}}
              info-response (protocol/process-message info-msg)]
          
          (is (= 3 (:id info-response)))
          (is (false? (get-in info-response [:result :isError])))
          (is (string? (get-in info-response [:result :content 0 :text])))
          
          ;; 4. Close session
          (let [close-msg {:jsonrpc "2.0"
                           :id 4
                           :method "tools/call"
                           :params {:name "close_session"
                                   :arguments {:session_id session-id}}}
                close-response (protocol/process-message close-msg)]
            
            (is (= 4 (:id close-response)))
            (is (false? (get-in close-response [:result :isError])))
            (is (nil? (browser/get-session session-id)))))))))

(deftest test-error-handling-via-protocol
  (testing "Protocol handles errors gracefully"
    ;; Try to navigate non-existent session
    (let [msg {:jsonrpc "2.0"
               :id 1
               :method "tools/call"
               :params {:name "navigate"
                       :arguments {:session_id "fake-session"
                                  :url "https://example.com"}}}
          response (protocol/process-message msg)]
      
      (is (= 1 (:id response)))
      (is (nil? (:error response))) ; No protocol error
      (is (true? (get-in response [:result :isError]))) ; But tool error
      ))
  
  (testing "Invalid JSON-RPC requests return errors"
    (let [bad-msg {:id 1
                   :method "test"}
          response (protocol/process-message bad-msg)]
      
      (is (some? (:error response)))
      (is (= -32600 (get-in response [:error :code]))))))

(deftest test-concurrent-tool-calls
  (testing "Can handle multiple tool calls in sequence"
    (let [messages [(protocol/process-message
                     {:jsonrpc "2.0"
                      :id 1
                      :method "initialize"
                      :params {}})
                   (protocol/process-message
                     {:jsonrpc "2.0"
                      :id 2
                      :method "tools/list"})
                   (protocol/process-message
                     {:jsonrpc "2.0"
                      :id 3
                      :method "tools/call"
                      :params {:name "browse_page"
                              :arguments {:url "https://example.com"}}})]]
      
      ;; All should succeed
      (is (every? some? messages))
      (is (every? #(nil? (:error %)) messages))
      (is (= [1 2 3] (map :id messages))))))
