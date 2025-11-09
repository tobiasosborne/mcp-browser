(ns mcp-browser.browser-management-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [mcp-browser.browser-management :as bm]))

;; Test Fixtures
(defn cleanup-sessions-fixture
  "Cleanup all sessions before AND after each test"
  [f]
  (bm/cleanup-all-sessions)  ; Clean before test
  (Thread/sleep 50)          ; Give cleanup time to complete
  (try
    (f)
    (finally
      (bm/cleanup-all-sessions)  ; Clean after test
      (Thread/sleep 50))))       ; Give cleanup time to complete

(use-fixtures :each cleanup-sessions-fixture)

;; ============================================================================
;; Process Spawning Tests
;; ============================================================================

(deftest test-spawn-browser-process
  (testing "Successfully spawns w3m process (or mock)"
    (let [options {:mock? (not (bm/validate-browser-available :w3m))}
          process-info (bm/spawn-browser-process :w3m "https://example.com" options)]
      (is (some? process-info))
      (is (= :w3m (:browser-type process-info)))
      (is (some? (:process process-info)))
      (is (some? (:started-at process-info)))
      (bm/kill-process process-info)))
  
  (testing "Successfully spawns lynx process (or mock)"
    (let [options {:mock? (not (bm/validate-browser-available :lynx))}
          process-info (bm/spawn-browser-process :lynx "https://example.com" options)]
      (is (some? process-info))
      (is (= :lynx (:browser-type process-info)))
      (is (some? (:process process-info)))
      (bm/kill-process process-info)))
  
  (testing "Handles invalid browser type (when mock is disabled)"
    ;; Only test this if we're not in auto-mock mode
    (when (bm/validate-browser-available :w3m)
      (is (thrown? Exception
                   (bm/spawn-browser-process :firefox "https://example.com" {:mock? false})))))
  
  (testing "Returns process handle with metadata"
    (let [options {:mock? (not (bm/validate-browser-available :w3m))}
          process-info (bm/spawn-browser-process :w3m "https://example.com" options)]
      (is (contains? process-info :process))
      (is (contains? process-info :browser-type))
      (is (contains? process-info :url))
      (is (contains? process-info :started-at))
      (bm/kill-process process-info))))

;; ============================================================================
;; Process Lifecycle Tests
;; ============================================================================

(deftest test-process-lifecycle
  (testing "Process is alive after start (or completes quickly in mock)"
    (let [options {:mock? (not (bm/validate-browser-available :w3m))}
          process-info (bm/spawn-browser-process :w3m "https://example.com" options)]
      (Thread/sleep 50) ; Give process time to start
      ;; In mock mode, process may complete instantly, which is fine
      (is (or (bm/process-alive? process-info)
              (:mock? options)))
      (bm/kill-process process-info)))
  
  (testing "Process stops cleanly"
    (let [options {:mock? (not (bm/validate-browser-available :w3m))}
          process-info (bm/spawn-browser-process :w3m "https://example.com" options)]
      (is (true? (bm/kill-process process-info)))
      (Thread/sleep 100)
      (is (not (bm/process-alive? process-info)))))
  
  (testing "Can read process output"
    (let [options {:mock? (not (bm/validate-browser-available :w3m))}
          process-info (bm/spawn-browser-process :w3m "https://example.com" options)
          result (bm/read-process-output process-info)]
      (is (some? result))
      (is (contains? result :output))
      (is (contains? result :exit-code)))))

;; ============================================================================
;; Session Creation Tests
;; ============================================================================

(deftest test-session-creation
  (testing "Creates new session with unique ID"
    (let [session-id (bm/create-session :w3m)]
      (is (some? session-id))
      (is (string? session-id))))
  
  (testing "Initializes session state correctly"
    (let [session-id (bm/create-session :w3m)
          session (bm/get-session session-id)]
      (is (some? session))
      (is (= :w3m (:browser-type session)))
      (is (= [] (:history session)))
      (is (nil? (:current-url session)))
      (is (some? (:created-at session)))
      (is (some? (:last-activity session)))))
  
  (testing "Multiple sessions have unique IDs"
    (let [id1 (bm/create-session :w3m)
          id2 (bm/create-session :lynx)]
      (is (not= id1 id2)))))

;; ============================================================================
;; Session Retrieval Tests
;; ============================================================================

(deftest test-session-retrieval
  (testing "Retrieves existing session by ID"
    (let [session-id (bm/create-session :w3m)
          session (bm/get-session session-id)]
      (is (some? session))
      (is (= session-id (:id session)))))
  
  (testing "Returns nil for non-existent session"
    (is (nil? (bm/get-session "non-existent-id"))))
  
  (testing "Lists all active sessions"
    ;; Clean before this test to ensure isolation
    (bm/cleanup-all-sessions)
    (Thread/sleep 50)
    (let [id1 (bm/create-session :w3m)
          id2 (bm/create-session :lynx)
          sessions (bm/list-sessions)]
      (is (= 2 (count sessions)) 
          (str "Expected 2 sessions but got " (count sessions) ": " sessions))
      (is (contains? (set sessions) id1))
      (is (contains? (set sessions) id2))))
  
  (testing "Gets session count"
    ;; Clean before this test
    (bm/cleanup-all-sessions)
    (Thread/sleep 50)
    (bm/create-session :w3m)
    (bm/create-session :lynx)
    (is (= 2 (bm/session-count))
        (str "Expected 2 sessions but got " (bm/session-count)))))

;; ============================================================================
;; Session State Tracking Tests
;; ============================================================================

(deftest test-session-state-tracking
  (testing "Tracks current URL in session"
    (let [session-id (bm/create-session :w3m)]
      (bm/add-to-history session-id "https://example.com")
      (let [session (bm/get-session session-id)]
        (is (= "https://example.com" (:current-url session))))))
  
  (testing "Maintains navigation history"
    (let [session-id (bm/create-session :w3m)]
      (bm/add-to-history session-id "https://example.com")
      (bm/add-to-history session-id "https://another.com")
      (let [session (bm/get-session session-id)]
        (is (= 2 (count (:history session))))
        (is (= "https://another.com" (:current-url session))))))
  
  (testing "Updates session timestamp on activity"
    (let [session-id (bm/create-session :w3m)
          initial-time (:last-activity (bm/get-session session-id))]
      (Thread/sleep 10)
      (bm/update-session-activity session-id)
      (let [updated-time (:last-activity (bm/get-session session-id))]
        (is (> updated-time initial-time))))))

;; ============================================================================
;; Session Cleanup Tests
;; ============================================================================

(deftest test-session-cleanup
  (testing "Removes session from active list"
    (let [session-id (bm/create-session :w3m)]
      (is (= 1 (bm/session-count)))
      (bm/cleanup-session session-id)
      (is (= 0 (bm/session-count)))
      (is (nil? (bm/get-session session-id)))))
  
  (testing "Cleans up multiple sessions"
    (bm/create-session :w3m)
    (bm/create-session :lynx)
    (is (= 2 (bm/session-count)))
    (bm/cleanup-all-sessions)
    (is (= 0 (bm/session-count)))))

;; ============================================================================
;; Resource Limits Tests
;; ============================================================================

(deftest test-resource-limits
  (testing "Enforces max concurrent sessions"
    (let [options {:max-sessions 2}]
      (bm/create-session :w3m options)
      (bm/create-session :lynx options)
      (is (thrown? Exception
                   (bm/create-session :w3m options))))))

;; ============================================================================
;; Browser Operations Tests
;; ============================================================================

(deftest test-fetch-url
  (testing "Successfully fetches URL with w3m (or mock)"
    (let [result (bm/fetch-url :w3m "https://example.com")]
      (is (some? result))
      (is (contains? result :output))
      (is (string? (:output result)))
      (is (pos? (count (:output result))))))
  
  (testing "Successfully fetches URL with lynx (or mock)"
    (let [result (bm/fetch-url :lynx "https://example.com")]
      (is (some? result))
      (is (contains? result :output))
      (is (string? (:output result))))))

(deftest test-navigate-session
  (testing "Navigates session to URL"
    (let [session-id (bm/create-session :w3m)
          result (bm/navigate-session session-id "https://example.com")]
      (is (some? result))
      (let [session (bm/get-session session-id)]
        (is (= "https://example.com" (:current-url session)))
        (is (= 1 (count (:history session))))))))

;; ============================================================================
;; Utilities Tests
;; ============================================================================

(deftest test-session-stats
  (testing "Returns session statistics"
    (let [session-id (bm/create-session :w3m)
          stats (bm/get-session-stats session-id)]
      (is (some? stats))
      (is (= session-id (:id stats)))
      (is (= :w3m (:browser-type stats)))
      (is (contains? stats :uptime-ms))
      (is (contains? stats :history-size))
      (is (= 0 (:history-size stats))))))

(deftest test-validate-browser
  (testing "Validates browser availability"
    (is (boolean? (bm/validate-browser-available :w3m)))
    (is (boolean? (bm/validate-browser-available :lynx)))))
