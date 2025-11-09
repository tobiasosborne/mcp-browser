(ns mcp-browser.tools-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [mcp-browser.tools :as tools]
            [mcp-browser.browser-management :as browser]))

;; Test Fixtures
(defn cleanup-fixture
  "Cleanup all sessions after each test"
  [f]
  (try
    (f)
    (finally
      (browser/cleanup-all-sessions)
      (Thread/sleep 50))))

(use-fixtures :each cleanup-fixture)

;; ============================================================================
;; browse_page Tool Tests
;; ============================================================================

(deftest test-browse-page
  (testing "Successfully browses a page"
    (let [result (tools/execute-tool "browse_page" {:url "https://example.com"})]
      (is (some? result))
      (is (contains? result :content))
      (is (vector? (:content result)))
      (is (false? (:isError result)))
      (is (= "text" (:type (first (:content result)))))
      (is (string? (:text (first (:content result)))))))
  
  (testing "Browses with specified browser"
    (let [result (tools/execute-tool "browse_page" {:url "https://example.com"
                                                     :browser "lynx"})]
      (is (some? result))
      (is (false? (:isError result)))))
  
  (testing "Handles missing URL"
    (let [result (tools/execute-tool "browse_page" {})]
      ;; Should error or handle gracefully
      (is (some? result)))))

;; ============================================================================
;; create_session Tool Tests
;; ============================================================================

(deftest test-create-session
  (testing "Creates a new session"
    (let [result (tools/execute-tool "create_session" {})]
      (is (some? result))
      (is (false? (:isError result)))
      (is (contains? result :session_id))
      (is (string? (:session_id result)))
      (is (vector? (:content result)))
      (is (= "text" (:type (first (:content result)))))))
  
  (testing "Creates session with specified browser"
    (let [result (tools/execute-tool "create_session" {:browser "lynx"})]
      (is (some? result))
      (is (false? (:isError result)))
      (is (contains? result :session_id))
      ;; Verify session exists
      (is (some? (browser/get-session (:session_id result))))))
  
  (testing "Returns valid session ID"
    (let [result (tools/execute-tool "create_session" {})
          session-id (:session_id result)]
      (is (some? session-id))
      ;; Should be able to get session
      (is (some? (browser/get-session session-id))))))

;; ============================================================================
;; navigate Tool Tests
;; ============================================================================

(deftest test-navigate
  (testing "Navigates within a session"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)
          nav-result (tools/execute-tool "navigate" {:session_id session-id
                                                      :url "https://example.com"})]
      (is (some? nav-result))
      (is (false? (:isError nav-result)))
      (is (vector? (:content nav-result)))
      (is (string? (:text (first (:content nav-result)))))))
  
  (testing "Handles non-existent session"
    (let [result (tools/execute-tool "navigate" {:session_id "fake-id"
                                                  :url "https://example.com"})]
      (is (some? result))
      (is (true? (:isError result)))
      (is (re-find #"not found" (:text (first (:content result)))))))
  
  (testing "Updates session history"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)]
      (tools/execute-tool "navigate" {:session_id session-id
                                       :url "https://example.com"})
      (let [session (browser/get-session session-id)]
        (is (= 1 (count (:history session))))
        (is (= "https://example.com" (:current-url session)))))))

;; ============================================================================
;; get_session_info Tool Tests
;; ============================================================================

(deftest test-get-session-info
  (testing "Gets info for existing session"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)
          info-result (tools/execute-tool "get_session_info" {:session_id session-id})]
      (is (some? info-result))
      (is (false? (:isError info-result)))
      (is (vector? (:content info-result)))
      (let [text (:text (first (:content info-result)))]
        (is (string? text))
        (is (re-find #"Session ID:" text))
        (is (re-find #"Browser:" text))
        (is (re-find #"Uptime:" text)))))
  
  (testing "Handles non-existent session"
    (let [result (tools/execute-tool "get_session_info" {:session_id "fake-id"})]
      (is (some? result))
      (is (true? (:isError result)))
      (is (re-find #"not found" (:text (first (:content result)))))))
  
  (testing "Shows navigation history"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)]
      (tools/execute-tool "navigate" {:session_id session-id
                                       :url "https://example.com"})
      (let [info-result (tools/execute-tool "get_session_info" {:session_id session-id})
            text (:text (first (:content info-result)))]
        (is (re-find #"Pages Visited: 1" text))
        (is (re-find #"https://example.com" text))))))

;; ============================================================================
;; close_session Tool Tests
;; ============================================================================

(deftest test-close-session
  (testing "Closes existing session"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)
          close-result (tools/execute-tool "close_session" {:session_id session-id})]
      (is (some? close-result))
      (is (false? (:isError close-result)))
      (is (re-find #"closed" (:text (first (:content close-result)))))
      ;; Verify session is gone
      (is (nil? (browser/get-session session-id)))))
  
  (testing "Handles non-existent session"
    (let [result (tools/execute-tool "close_session" {:session_id "fake-id"})]
      (is (some? result))
      (is (true? (:isError result)))
      (is (re-find #"not found" (:text (first (:content result)))))))
  
  (testing "Cleans up session resources"
    (let [create-result (tools/execute-tool "create_session" {})
          session-id (:session_id create-result)
          initial-count (browser/session-count)]
      (tools/execute-tool "close_session" {:session_id session-id})
      (is (< (browser/session-count) initial-count)))))

;; ============================================================================
;; Unknown Tool Tests
;; ============================================================================

(deftest test-unknown-tool
  (testing "Handles unknown tool gracefully"
    (let [result (tools/execute-tool "unknown_tool" {})]
      (is (some? result))
      (is (true? (:isError result)))
      (is (re-find #"Unknown tool" (:text (first (:content result))))))))

;; ============================================================================
;; Integration Tests
;; ============================================================================

(deftest test-full-browsing-workflow
  (testing "Complete browsing session workflow"
    ;; 1. Create session
    (let [create-result (tools/execute-tool "create_session" {:browser "w3m"})
          session-id (:session_id create-result)]
      
      (is (false? (:isError create-result)))
      (is (some? session-id))
      
      ;; 2. Navigate to page
      (let [nav-result (tools/execute-tool "navigate" {:session_id session-id
                                                        :url "https://example.com"})]
        (is (false? (:isError nav-result)))
        (is (pos? (count (:text (first (:content nav-result)))))))
      
      ;; 3. Check session info
      (let [info-result (tools/execute-tool "get_session_info" {:session_id session-id})]
        (is (false? (:isError info-result)))
        (is (re-find #"Pages Visited: 1" (:text (first (:content info-result))))))
      
      ;; 4. Navigate to another page
      (tools/execute-tool "navigate" {:session_id session-id
                                       :url "https://example.org"})
      
      ;; 5. Verify history updated
      (let [info-result (tools/execute-tool "get_session_info" {:session_id session-id})]
        (is (re-find #"Pages Visited: 2" (:text (first (:content info-result))))))
      
      ;; 6. Close session
      (let [close-result (tools/execute-tool "close_session" {:session_id session-id})]
        (is (false? (:isError close-result)))
        (is (nil? (browser/get-session session-id)))))))

(deftest test-multiple-concurrent-sessions
  (testing "Can handle multiple sessions simultaneously"
    ;; Create multiple sessions
    (let [session1 (tools/execute-tool "create_session" {:browser "w3m"})
          session2 (tools/execute-tool "create_session" {:browser "lynx"})
          id1 (:session_id session1)
          id2 (:session_id session2)]
      
      (is (not= id1 id2))
      
      ;; Navigate each to different pages
      (tools/execute-tool "navigate" {:session_id id1 :url "https://example.com"})
      (tools/execute-tool "navigate" {:session_id id2 :url "https://example.org"})
      
      ;; Verify they're independent
      (let [session1-data (browser/get-session id1)
            session2-data (browser/get-session id2)]
        (is (= "https://example.com" (:current-url session1-data)))
        (is (= "https://example.org" (:current-url session2-data))))
      
      ;; Clean up
      (tools/execute-tool "close_session" {:session_id id1})
      (tools/execute-tool "close_session" {:session_id id2}))))
