(ns mcp-browser.tools
  (:require [mcp-browser.browser-management :as browser]
            [clojure.tools.logging :as log]))

;; ============================================================================
;; Tool Execution
;; ============================================================================

(defn execute-browse-page
  "Executes the browse_page tool"
  [{:keys [url browser] :or {browser "w3m"}}]
  (log/info "Browsing page:" url "with" browser)
  (try
    (let [browser-type (keyword browser)
          result (browser/fetch-url browser-type url)]
      {:content [{:type "text"
                  :text (:output result)}]
       :isError false})
    (catch Exception e
      (log/error e "Error browsing page" url)
      {:content [{:type "text"
                  :text (str "Error fetching page: " (.getMessage e))}]
       :isError true})))

(defn execute-create-session
  "Executes the create_session tool"
  [{:keys [browser] :or {browser "w3m"}}]
  (log/info "Creating browsing session with" browser)
  (try
    (let [browser-type (keyword browser)
          session-id (browser/create-session browser-type)]
      {:content [{:type "text"
                  :text (str "Created session: " session-id "\n"
                            "Use this session_id to navigate to URLs.\n"
                            "Call get_session_info to see session details.\n"
                            "Call close_session when done.")}]
       :isError false
       :session_id session-id})
    (catch Exception e
      (log/error e "Error creating session")
      {:content [{:type "text"
                  :text (str "Error creating session: " (.getMessage e))}]
       :isError true})))

(defn execute-navigate
  "Executes the navigate tool"
  [{:keys [session_id url]}]
  (log/info "Navigating session" session_id "to" url)
  (try
    (if-let [session (browser/get-session session_id)]
      (let [result (browser/navigate-session session_id url)]
        {:content [{:type "text"
                    :text (:output result)}]
         :isError false})
      {:content [{:type "text"
                  :text (str "Session not found: " session_id)}]
       :isError true})
    (catch Exception e
      (log/error e "Error navigating session" session_id)
      {:content [{:type "text"
                  :text (str "Error navigating: " (.getMessage e))}]
       :isError true})))

(defn execute-get-session-info
  "Executes the get_session_info tool"
  [{:keys [session_id]}]
  (log/info "Getting info for session" session_id)
  (try
    (if-let [stats (browser/get-session-stats session_id)]
      (let [info (str "Session ID: " (:id stats) "\n"
                     "Browser: " (name (:browser-type stats)) "\n"
                     "Created: " (java.util.Date. (:created-at stats)) "\n"
                     "Last Activity: " (java.util.Date. (:last-activity stats)) "\n"
                     "Uptime: " (/ (:uptime-ms stats) 1000.0) " seconds\n"
                     "Pages Visited: " (:history-size stats) "\n"
                     "Current URL: " (or (:current-url stats) "none") "\n"
                     "Process Alive: " (:process-alive? stats))]
        {:content [{:type "text"
                    :text info}]
         :isError false})
      {:content [{:type "text"
                  :text (str "Session not found: " session_id)}]
       :isError true})
    (catch Exception e
      (log/error e "Error getting session info" session_id)
      {:content [{:type "text"
                  :text (str "Error getting session info: " (.getMessage e))}]
       :isError true})))

(defn execute-close-session
  "Executes the close_session tool"
  [{:keys [session_id]}]
  (log/info "Closing session" session_id)
  (try
    (if (browser/get-session session_id)
      (do
        (browser/cleanup-session session_id)
        {:content [{:type "text"
                    :text (str "Session closed: " session_id)}]
         :isError false})
      {:content [{:type "text"
                  :text (str "Session not found: " session_id)}]
       :isError true})
    (catch Exception e
      (log/error e "Error closing session" session_id)
      {:content [{:type "text"
                  :text (str "Error closing session: " (.getMessage e))}]
       :isError true})))

;; ============================================================================
;; Tool Router
;; ============================================================================

(defn execute-tool
  "Routes tool execution to the appropriate handler"
  [tool-name arguments]
  (log/info "Executing tool:" tool-name)
  (case tool-name
    "browse_page"
    (execute-browse-page arguments)
    
    "create_session"
    (execute-create-session arguments)
    
    "navigate"
    (execute-navigate arguments)
    
    "get_session_info"
    (execute-get-session-info arguments)
    
    "close_session"
    (execute-close-session arguments)
    
    ;; Unknown tool
    {:content [{:type "text"
                :text (str "Unknown tool: " tool-name)}]
     :isError true}))
