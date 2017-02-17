(ns litebook.comp.repl
  (:require [replumb.core :as replumb]
            [replumb.repl :as repl]
            [re-frame.core :as re-frame]
            [cljs.tools.reader.reader-types :as rt]
            [cljs.tools.reader :as rdr]
            [litebook.core.config :as conf]
            [litebook.comp.pref :as pref]
            [litebook.comp.selfhosted :as sh]
            [litebook.comp.remoterepl :as remote]))

(def nrepl-server (js/require "jg-nrepl-client/src/nrepl-server"))
(def electron (js/require "electron"))
(def nrepl-pid (atom nil))
(def kill (js/require "tree-kill"))
(def cp (js/require "child_process"))

(def repl-map {:self       "Self Hosted CLJS REPL"
               :remote     "Remote nREPL"
               :self-clj   "Self Hosted nREPL"})
;;map of repl status and a description
(def repl-status-map {:connected  "Connected"
                      :connecting "Connecting"})
(defn read-chars
  [reader]
  "Adapted from Planck's implementation."
  (lazy-seq
   (when-let [ch (rt/read-char reader)]
     (cons ch (read-chars reader)))))

(defn read-next
  [content]
  "Adapted from planck's implementation. Given a string returns a vector of two
elements, First element would be the first valid form/expr in content
and second element is the remaining portion in the content string."
  (let [reader (rt/string-push-back-reader content)]
    [(repl/read {:read-cond :allow :features #{:cljs}}  reader)
     (apply str (read-chars reader))]))

(defn read-all
  [content]
  "Given a string <content> , returns a vector of all forms/expressions
 in the content string.If there is an error in parsing any of the form,
 remaining of the content will not be processed."
  (let [first-pass (read-next content)]
    (loop [current-pass first-pass acc []]
      (let [[parsed remaining] current-pass
            to-parse           remaining
            more?              (not (empty? (clojure.string/trim to-parse)))
            parsed-so-far      (conj acc parsed)]
        (if-not more?
          parsed-so-far
          (recur (read-next to-parse) parsed-so-far))))))

(defn make
  [spec]
  "Given a spec, makes the right REPL configuration set.
Spec - Must have a :type key. Supported types are :self :remote :self-clj"
  (let [type  (:type spec)]
    (cond
      (= :self type)     (sh/make spec)
      (= :remote type)   (remote/make spec)
      (= :self-clj type) (remote/make spec))))

(defn default-repl
  []
  (make {:type :self}))

(defn change-repl
  [state repl-type make-fn]
  "Given the current state of app, a repl type the user wants to switch to
   and a make function that creates a repl, modifies the app state to use the new
   repl type"
  (let [repls     (:repls state)
        loaded    (repl-type repls)
        ;; Change repl status to :connected
        mstate    (pref/update-preferences
                   state
                   #(assoc-in % [:repl-config :repl-state] :connected))
        new-repl  (make-fn)]
    ;; Code complete should switch to the newly selected repl type
    (re-frame/dispatch [:configure-codecomplete]);todo:efx handlers
    (if loaded
      mstate
      (assoc mstate :repls (assoc repls repl-type new-repl)))))

(defn cleanup
  [callback]
  "  Called when the user hits close button. Cleans up any running nrepl server.
If a callback event is provided,callback event is fired when the nrepl process
is terminated or immediately when there is no nrepl process running"
  (if @nrepl-pid
    (if conf/on-windows?
      (do
        (.execSync cp (str "taskkill /pid " @nrepl-pid " /T /F"))
        (when callback (re-frame/dispatch [callback])))
      (re-frame/dispatch [:quit-nrepl callback]))
    (re-frame/dispatch [callback])))

(re-frame/reg-event-db
 :switch-to-self
 (fn [state _]
   "Event handler to switch to selfhosted cljs repl"
   (change-repl state :self #(make {:type :self}))))

(re-frame/reg-event-db
 :switch-to-nrepl-port
 (fn [state [_ port]]
   "Event handler to swtich to a remote nrepl"
   (change-repl state :remote #(make {:type :remote :port (js/parseInt port)}))))

(re-frame/reg-event-db
 :switch-to-hosted-nrepl-port
 (fn [state [_ port]]
   "Event handler to connect to the self hosted nrepl process"
   (change-repl state :self-clj #(make {:type :self-clj :port (js/parseInt port)}))))

(re-frame/reg-event-db
 :prep-nrepl-server
 (fn [state [_ port]]
   "Event handler to start a self hosted nrepl server"
   ;;TODO: add support for multiple nrepl instances. not a must have
   (cleanup :start-nrepl-server)
   state))

(re-frame/reg-event-db
 :start-nrepl-server
 (fn [state [_ port]]
   "Event handler to start a self hosted nrepl server"
   ;;TODO: add support for multiple nrepl instances. not a must have
   (println "Starting a server")
   (.start nrepl-server
           (clj->js {:port port
                     ;;hook on 2 the project.clj in selfhosted folder
                     :projectPath (str (.cwd js/process) "\\selfhosted")})
           (fn [err server-state]
             (re-frame/dispatch [:nrepl-started server-state])))
   state))

(re-frame/reg-event-db
 :nrepl-started
 (fn [state [_ server-state]]
   (if server-state
     (let [mstate  (assoc state :nrepl-config server-state)
           _       (reset! nrepl-pid (.-pid (.-proc (:nrepl-config mstate))))
           _       (println (str "Server running with pid ="  @nrepl-pid))]
       (re-frame/dispatch [:switch-to-hosted-nrepl-port (.-port server-state)])
       mstate)
     state)))

(re-frame/reg-event-db
 :quit-nrepl
 (fn [state [_ callback-event]]
   (let [port  (.-port (:nrepl-config state))
         _    (println (str ")Quitting @ " (.-pid (.-proc (:nrepl-config state)))))]
     (.stop nrepl-server (:nrepl-config state)
            (fn [e m] (when callback-event
                        (re-frame/dispatch [callback-event]))))
     state)))
