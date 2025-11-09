(defproject mcp-browser "0.1.0-SNAPSHOT"
  :description "MCP Server for web browsing with text-based browsers"
  :url "none yet"
  :license {:name "EPL-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [cheshire "5.12.0"]              ; JSON parsing
                 [org.clojure/core.async "1.6.681"] ; Async operations
                 [org.clojure/tools.logging "1.3.0"]] ; Logging
  :main ^:skip-aot mcp-browser.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.clojure/test.check "1.1.1"]]}})
