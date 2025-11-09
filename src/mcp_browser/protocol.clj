(ns mcp-browser.protocol
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [mcp-browser.tools :as tools])
  (:import [java.io BufferedReader BufferedWriter]))

;; ============================================================================
;; JSON-RPC 2.0 Protocol Implementation
;; ============================================================================

(def ^:private protocol-version "2024-11-05")

(def ^:private server-info
  {:name "mcp-browser"
   :version "0.1.0"})

;; ============================================================================
;; Message Reading/Writing
;; ============================================================================

(defn read-message
  "Reads a JSON-RPC message from stdin"
  []
  (try
    (when-let [line (read-line)]
      (log/debug "Received:" line)
      (json/parse-string line true))
    (catch Exception e
      (log/error e "Failed to parse JSON-RPC message")
      nil)))

(defn write-message
  "Writes a JSON-RPC message to stdout"
  [message]
  (try
    (let [json-str (json/generate-string message)]
      (println json-str)
      (flush)
      (log/debug "Sent:" json-str))
    (catch Exception e
      (log/error e "Failed to write JSON-RPC message"))))

(defn error-response
  "Creates a JSON-RPC error response"
  [id code message & [data]]
  {:jsonrpc "2.0"
   :id id
   :error (merge
           {:code code
            :message message}
           (when data {:data data}))})

(defn success-response
  "Creates a JSON-RPC success response"
  [id result]
  {:jsonrpc "2.0"
   :id id
   :result result})

;; ============================================================================
;; MCP Protocol Handlers
;; ============================================================================

(defn handle-initialize
  "Handles the MCP initialize request"
  [params]
  (log/info "Initializing MCP server" params)
  {:protocolVersion protocol-version
   :capabilities {:tools {}
                  :logging {}}
   :serverInfo server-info})

(defn handle-list-tools
  "Returns the list of available tools"
  []
  (log/info "Listing tools")
  {:tools
   [{:name "browse_page"
     :description "Fetch and display a webpage as text using w3m or lynx"
     :inputSchema {:type "object"
                   :properties {:url {:type "string"
                                     :description "The URL to fetch"}
                               :browser {:type "string"
                                        :enum ["w3m" "lynx"]
                                        :default "w3m"
                                        :description "Browser to use (w3m or lynx)"}}
                   :required ["url"]}}
    
    {:name "create_session"
     :description "Create a new browsing session for interactive navigation"
     :inputSchema {:type "object"
                   :properties {:browser {:type "string"
                                         :enum ["w3m" "lynx"]
                                         :default "w3m"
                                         :description "Browser to use"}}
                   :required []}}
    
    {:name "navigate"
     :description "Navigate to a URL within an existing session"
     :inputSchema {:type "object"
                   :properties {:session_id {:type "string"
                                            :description "Session ID from create_session"}
                               :url {:type "string"
                                    :description "URL to navigate to"}}
                   :required ["session_id" "url"]}}
    
    {:name "get_session_info"
     :description "Get information about a browsing session"
     :inputSchema {:type "object"
                   :properties {:session_id {:type "string"
                                            :description "Session ID"}}
                   :required ["session_id"]}}
    
    {:name "close_session"
     :description "Close a browsing session and cleanup resources"
     :inputSchema {:type "object"
                   :properties {:session_id {:type "string"
                                            :description "Session ID to close"}}
                   :required ["session_id"]}}]})

(defn handle-call-tool
  "Handles tool execution - delegates to tool layer"
  [tool-name arguments]
  (log/info "Calling tool" tool-name "with args" arguments)
  (try
    (tools/execute-tool tool-name arguments)
    (catch Exception e
      (log/error e "Error executing tool" tool-name)
      {:content [{:type "text"
                  :text (str "Error executing tool: " (.getMessage e))}]
       :isError true})))

;; ============================================================================
;; Request Routing
;; ============================================================================

(defn route-request
  "Routes a JSON-RPC request to the appropriate handler"
  [request]
  (let [{:keys [id method params]} request]
    (try
      (case method
        "initialize"
        (success-response id (handle-initialize params))
        
        "tools/list"
        (success-response id (handle-list-tools))
        
        "tools/call"
        (let [{:keys [name arguments]} params]
          (success-response id (handle-call-tool name arguments)))
        
        "ping"
        (success-response id {})
        
        ;; Unknown method
        (error-response id -32601 (str "Method not found: " method)))
      
      (catch Exception e
        (log/error e "Error handling request" request)
        (error-response id -32603 
                       "Internal error" 
                       {:message (.getMessage e)})))))

;; ============================================================================
;; Main Event Loop
;; ============================================================================

(defn process-message
  "Processes a single message and returns response"
  [message]
  (when message
    (cond
      ;; Check for required fields
      (not (:jsonrpc message))
      (error-response nil -32600 "Invalid Request: missing jsonrpc field")
      
      (not= "2.0" (:jsonrpc message))
      (error-response (:id message) -32600 "Invalid Request: jsonrpc must be 2.0")
      
      (not (:method message))
      (error-response (:id message) -32600 "Invalid Request: missing method field")
      
      ;; Valid request
      :else
      (route-request message))))

(defn start-server
  "Starts the MCP server event loop"
  []
  (log/info "Starting MCP Browser Server")
  (log/info "Server info:" server-info)
  (log/info "Protocol version:" protocol-version)
  
  (loop []
    (when-let [message (read-message)]
      (when-let [response (process-message message)]
        (write-message response))
      (recur)))
  
  (log/info "MCP Browser Server stopped"))

;; ============================================================================
;; Utilities
;; ============================================================================

(defn validate-request
  "Validates a JSON-RPC request structure"
  [request]
  (and (map? request)
       (= "2.0" (:jsonrpc request))
       (string? (:method request))
       (or (nil? (:id request))
           (string? (:id request))
           (number? (:id request)))))
