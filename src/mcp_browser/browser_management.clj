(ns mcp-browser.browser-management
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.util UUID]
           [java.util.concurrent TimeUnit]))

;; ============================================================================
;; State Management
;; ============================================================================

(def ^:private sessions
  "Atom holding all active browser sessions"
  (atom {}))

(def ^:private default-config
  {:browser-type :w3m
   :timeout-ms 30000
   :max-sessions 10
   :user-agent "MCP-Browser/1.0"})

;; ============================================================================
;; Utilities (needed early)
;; ============================================================================

(defn validate-browser-available
  "Checks if a browser binary is available on the system"
  [browser-type]
  (try
    (let [binary (name browser-type)
          is-windows? (str/includes? (System/getProperty "os.name") "Windows")
          command (if is-windows? ["where" binary] ["which" binary])
          result (apply shell/sh command)]
      (zero? (:exit result)))
    (catch Exception _
      false)))

;; ============================================================================
;; Process Spawning
;; ============================================================================

(defn- get-browser-command
  "Returns the command and args for spawning a browser"
  [browser-type url options]
  (let [is-windows? (str/includes? (System/getProperty "os.name") "Windows")
        use-mock? (or (:mock? options) 
                      (and is-windows? 
                           (not (validate-browser-available browser-type))))]
    (if use-mock?
      ;; Mock mode: use echo to simulate browser output
      (if is-windows?
        ["cmd" "/c" "echo" (str "Mock browser output for " url)]
        ["echo" (str "Mock browser output for " url)])
      ;; Real browser mode
      (case browser-type
        :w3m ["w3m" "-dump" url]
        :lynx ["lynx" "-dump" "-nolist" url]
        (throw (ex-info "Unknown browser type" {:browser-type browser-type}))))))

(defn spawn-browser-process
  "Spawns a browser process and returns process info"
  ([browser-type] (spawn-browser-process browser-type nil))
  ([browser-type url]
   (spawn-browser-process browser-type url {}))
  ([browser-type url options]
   (try
     (let [command (get-browser-command browser-type url options)
           process-builder (ProcessBuilder. ^java.util.List command)
           process (.start process-builder)]
       {:process process
        :browser-type browser-type
        :url url
        :started-at (System/currentTimeMillis)
        :pid (try (.pid process) (catch Exception _ nil))})
     (catch Exception e
       (throw (ex-info "Failed to spawn browser process"
                       {:browser-type browser-type
                        :url url
                        :error (.getMessage e)}
                       e))))))

(defn process-alive?
  "Checks if a browser process is still running"
  [process-info]
  (when-let [process (:process process-info)]
    (.isAlive process)))

(defn kill-process
  "Kills a browser process"
  [process-info]
  (when-let [process (:process process-info)]
    (try
      (.destroy process)
      (when-not (.waitFor process 5 TimeUnit/SECONDS)
        (.destroyForcibly process))
      true
      (catch Exception e
        (throw (ex-info "Failed to kill process"
                        {:error (.getMessage e)}
                        e))))))

(defn read-process-output
  "Reads output from a process"
  [process-info]
  (when-let [process (:process process-info)]
    (try
      (let [input-stream (.getInputStream process)
            output (slurp input-stream)]
        {:output output
         :exit-code (.waitFor process 30 TimeUnit/SECONDS)})
      (catch Exception e
        (throw (ex-info "Failed to read process output"
                        {:error (.getMessage e)}
                        e))))))

;; ============================================================================
;; Session Management
;; ============================================================================

(defn generate-session-id
  "Generates a unique session ID"
  []
  (str (UUID/randomUUID)))

(defn create-session
  "Creates a new browser session"
  ([browser-type] (create-session browser-type {}))
  ([browser-type options]
   (let [session-count (count @sessions)
         max-sessions (get options :max-sessions (:max-sessions default-config))]
     (when (>= session-count max-sessions)
       (throw (ex-info "Maximum sessions reached"
                       {:current session-count
                        :max max-sessions})))
     (let [session-id (generate-session-id)
           session {:id session-id
                    :browser-type browser-type
                    :created-at (System/currentTimeMillis)
                    :last-activity (System/currentTimeMillis)
                    :history []
                    :current-url nil
                    :options options
                    :process nil}]
       (swap! sessions assoc session-id session)
       session-id))))

(defn get-session
  "Retrieves a session by ID"
  [session-id]
  (get @sessions session-id))

(defn list-sessions
  "Lists all active sessions"
  []
  (keys @sessions))

(defn session-count
  "Returns the number of active sessions"
  []
  (count @sessions))

(defn update-session
  "Updates a session's state"
  [session-id update-fn]
  (swap! sessions update session-id update-fn)
  (get @sessions session-id))

(defn update-session-activity
  "Updates the last activity timestamp for a session"
  [session-id]
  (update-session session-id
                  #(assoc % :last-activity (System/currentTimeMillis))))

(defn add-to-history
  "Adds a URL to session history"
  [session-id url]
  (update-session session-id
                  #(-> %
                       (update :history conj url)
                       (assoc :current-url url)
                       (assoc :last-activity (System/currentTimeMillis)))))

(defn cleanup-session
  "Cleans up a session and kills associated process"
  [session-id]
  (when-let [session (get-session session-id)]
    (when-let [process (:process session)]
      (try
        (kill-process process)
        (catch Exception e
          (println "Error killing process:" (.getMessage e)))))
    (swap! sessions dissoc session-id)
    true))

(defn cleanup-all-sessions
  "Cleans up all active sessions"
  []
  (doseq [session-id (list-sessions)]
    (cleanup-session session-id)))

;; ============================================================================
;; Browser Operations
;; ============================================================================

(defn fetch-url
  "Fetches a URL using the specified browser in dump mode"
  ([url] (fetch-url :w3m url))
  ([browser-type url]
   (let [process-info (spawn-browser-process browser-type url)
         result (read-process-output process-info)]
     (kill-process process-info)
     result)))

(defn navigate-session
  "Navigates a session to a URL"
  [session-id url]
  (when-let [session (get-session session-id)]
    (let [browser-type (:browser-type session)
          process-info (spawn-browser-process browser-type url)
          result (read-process-output process-info)]
      (update-session session-id
                      #(assoc % :process process-info))
      (add-to-history session-id url)
      result)))

;; ============================================================================
;; Utilities (continued)
;; ============================================================================

(defn get-session-stats
  "Returns statistics about a session"
  [session-id]
  (when-let [session (get-session session-id)]
    {:id (:id session)
     :browser-type (:browser-type session)
     :created-at (:created-at session)
     :last-activity (:last-activity session)
     :uptime-ms (- (System/currentTimeMillis) (:created-at session))
     :history-size (count (:history session))
     :current-url (:current-url session)
     :process-alive? (process-alive? (:process session))}))
