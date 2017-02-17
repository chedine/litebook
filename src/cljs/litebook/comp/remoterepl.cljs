(ns litebook.comp.remoterepl
  (:require [cljs.tools.reader.reader-types :as rt]
            [re-frame.core :as re-frame]
            [cljs.tools.reader :as rdr]))

(def nrepl (js/require "jg-nrepl-client"))

(re-frame/reg-event-db
 :nrepl-con-available
 ;;Event is fired whenever the make function of remoterepl is invoked
 ;; Ideally when the user switches from a different repl type to this.
 (fn [db [_ client session-var]]
   (println "Cloning a new session")
   (.clone client (fn [err msg]
                    (re-frame/dispatch
                     [:nrepl-session-cloned client session-var err msg])))
   db))

(re-frame/reg-event-db
 :nrepl-session-cloned
 (fn [db [_ client session-atom err msg]]
   ;;msg is a js array, with 1st el containing an attribute new-session
   ;;new-session is the session id that we want to use for further comms
   (let [session-id (aget (first (js->clj msg))
                          "new-session")]
     (reset! session-atom session-id)
     db)))

(defn eval-form
  [printer content client session]
  "Given a form and a print function, evaluates and invokes printer with the results"
    (.send client  (clj->js
                    {:op "eval" :code content
                     :session @session :ns "user"}) (fn [e m] (printer m))))

(defn format-results
  [results]
  "Re-formats the results (given by nrepl operation) into a common format"
  (let [res    (aget results 0)
        value  (aget res "value")
        err    (aget res "ex")
        success? (if value true false)]
    {:success? success?
     :value (rdr/read-string value) ;;reads back the pretty printed str value
     :error (if success? {}
                {:cause (aget res "root-ex")
                 :message err})}
    ))

(defn- when-suggestions-ready
  [cb results err]
  "Takes top 15 suggestions and invokes the cb (callback) fn with them"
  (let [completions     (aget (first results) "completions")
        ;;the structure of the response largely depends on the middleware configured in
        ;;the nrepl config. A common stack is cider-nrepl with std middleware configs
        ;;results is a javascript array of one element. First element has an attribute
        ;;called completions which is an array of objects. Each candidate object has an
        ;;attribute called candidate which is the suggestion that we would want to show
        candidated?     (.hasOwnProperty (first completions) "candidate")
        top-candidates  (if candidated?
                          (take 15 (mapv #(aget % "candidate") completions))
                          (take 15 completions))]
    (cb top-candidates)))

(defn code-complete
  [text cb client session]
  "Uses underlying nREPL connection to get a list of valid suggestions for
the current state of the editor
text - is the text for which suggestions are required
cb - code mirror callback function to b invoked when suggestions are ready
client - nrepl client handle
session - nrepl connection identifier."
  (let [callback-fn (fn [err res] (when-suggestions-ready cb res err))
        payload     (clj->js
                     {:op "complete"
                      :symbol text
                      :context "same"
                      :session @session :ns "user"})
        suggestions (.send client payload callback-fn)]
    nil))

(defn make
  [spec]
  "Manufactures a remote repl object(kind of).
Spec - a map with mandator port key. Host is assumed to localhost(for now)"
  (let [session   (atom nil)
        client    (.connect nrepl (clj->js {:port (:port spec)}))
        _         (.once client "connect"
                         #(re-frame/dispatch [:nrepl-con-available client session]))]
    {:session    session
     :eval-fn    (fn [printer content]
                   (eval-form printer content client session))
     :formatter  format-results
     :suggest-fn (fn [content cb]
                   (code-complete content cb client session))}))
