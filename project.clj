(defproject clens "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [traversy "0.4.0"]
                 [com.stuartsierra/dependency "0.2.0"]]
  :profiles {:dev {:plugins [[com.jakemccrary/lein-test-refresh "0.10.0"]
                             [lein-cljsbuild "1.1.4"]
                             [lein-doo "0.1.7"]]
                   :aliases {"test-cljs" ["doo" "phantom" "test" "once"]
                             "test-all"  ["do" ["test"] ["test-cljs"]]
                             "auto-cljs" ["doo" "phantom" "test" "auto"]
                             "auto"      ["test-refresh"]}}}
  :cljsbuild {:builds        {:test {:source-paths ["src" "test"]
                                     :compiler     {:output-to     "target/cljs/testable.js"
                                                    :main          detox.test-runner
                                                    :optimizations :whitespace}}}
              :test-commands {"unit-tests" ["phantomjs" "target/cljs/testable.js"]}})

