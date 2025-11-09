(ns mcp-browser.core
  (:require [mcp-browser.protocol :as protocol]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn -main
  "Main entry point for MCP Browser Server"
  [& args]
  (try
    ;; Setup logging
    (log/info "MCP Browser Server starting...")
    
    ;; Start the protocol server (blocking)
    (protocol/start-server)
    
    (catch Exception e
      (log/error e "Fatal error in MCP Browser Server")
      (System/exit 1))))
