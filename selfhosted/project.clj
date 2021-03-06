(defproject Demo "0.1.0-SNAPSHOT"
  :description "An internal project config"
  :url "http://demo.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
				 [com.cemerick/pomegranate "0.3.1"]
				 [org.clojure/clojurescript "1.9.198"]
                 ]
  :profiles {:repl {:dependencies [[cider/cider-nrepl "0.14.0"]
								   [org.clojure/tools.nrepl "0.2.12"]]}}
  :repl-options {:nrepl-middleware
                 [cider.nrepl.middleware.apropos/wrap-apropos
                  cider.nrepl.middleware.classpath/wrap-classpath
                  cider.nrepl.middleware.complete/wrap-complete
                  cider.nrepl.middleware.debug/wrap-debug
                  cider.nrepl.middleware.format/wrap-format
                  cider.nrepl.middleware.info/wrap-info
                  cider.nrepl.middleware.inspect/wrap-inspect
                  cider.nrepl.middleware.macroexpand/wrap-macroexpand
                  cider.nrepl.middleware.ns/wrap-ns
              ;    cider.nrepl.middleware.pprint/wrap-pprint
               ;   cider.nrepl.middleware.pprint/wrap-pprint-fn
                  cider.nrepl.middleware.refresh/wrap-refresh
                  cider.nrepl.middleware.resource/wrap-resource
                  cider.nrepl.middleware.stacktrace/wrap-stacktrace
                  cider.nrepl.middleware.test/wrap-test
                  cider.nrepl.middleware.trace/wrap-trace
                  cider.nrepl.middleware.out/wrap-out
                  cider.nrepl.middleware.undef/wrap-undef
                  cider.nrepl.middleware.version/wrap-version]})
