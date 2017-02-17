(defproject litebook "0.1.0-alpha1"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.198"]
                 [cljsjs/nodejs-externs "1.0.4-1"]
                 [re-frame "0.8.0"]
                 [replumb "0.2.1"]
                 [cljsjs/react "15.3.1-1"]
                 [cljsjs/codemirror "5.11.0-1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [com.rpl/specter "0.12.0"]
                 [reagent "0.6.0-rc"]]

  :plugins [[lein-cljsbuild "1.1.3"]]

  :min-lein-version "2.5.3"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "app/js/r/renderer.js"
                                        :output-dir    "app/js/r/out"
                                        :asset-path    "js/r/out"
                                        :optimizations :none
                                        :pretty-print  true
										:main "litebook.core"
										;:externs ["externs/misc.js"]
                                        :cache-analysis true}}
                       :main {:source-paths ["src/cljs-main"]
                              :compiler {:output-to     "app/main.js"
                                         :output-dir    "app/js/m/out"
                                         :asset-path    "js/r/out"
                                         :optimizations :simple
                                         :target  :nodejs
                                         :pretty-print  true
										 :main "litebook.main"
                                         :cache-analysis true}}}}

  :clean-targets ^{:protect false} [:target-path "out" "app/js/r" "app/js/m" "app/main.js"]
  
  :figwheel {:css-dirs ["app/css"]}
  
  :profiles {:dev {:cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                                              :compiler {:source-map true
                                                         :main       "litebook.core"
                                                         :verbose true}
                                              :figwheel {:on-jsload "litebook.core/mount-root"}}
										}}
                   :source-paths ["src/cljs"]

                   :dependencies [[figwheel-sidecar "0.5.0-6"]]

                   :plugins [[lein-ancient "0.6.8"]
                             [lein-kibit "0.1.2"]
                             [lein-cljfmt "0.4.1"]
                             [lein-figwheel "0.5.0-6"]]}

             :production {:cljsbuild {:builds {:app {:compiler {:optimizations :none
                                                                :main          "litebook.core"
                                                                :parallel-build true
                                                                :cache-analysis false
                                                                :closure-defines {"goog.DEBUG" false}
                                                                :externs ["externs/misc.js"]
                                                                :pretty-print false
															   }
                                                     :source-paths ["src/cljs"]
													 }}}}}
  )
 