(ns litebook.frp.handlers
  (:require [re-frame.core :as re-frame]
            [litebook.core.state :as s]
            [cljs.reader :as reader]
            [litebook.comp.repl :as repl]
            [litebook.comp.notebook :as notes]))

(def jp (js/require "fs-jetpack"))

(def file-filters (clj->js {:title "Select a notebook"
                            :filters [{:name "nbk" :extensions ["nbk"]}]}))
(def cp (js/require "child_process"))

(def nrepl-server (js/require "jg-nrepl-client/src/nrepl-server"))
							
(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   s/app-state))

(re-frame/reg-event-db
 :init-app
 (fn [state _]
   (let [blank-note (notes/new-note state)]
     (-> state
         (assoc :notebook blank-note)
         (assoc :repls {:self (repl/default-repl)})))))

(re-frame/reg-event-db
 :mnu-file-new
 (fn [state _]
   (assoc state :notebook (notes/new-note state))))

(re-frame/reg-event-db
 :mnu-file-save
 (fn [state [_ dialog]]
   (let [file-link (get-in state [:notebook :file-link])]
     (if (not (nil? file-link))
       (re-frame/dispatch [:save-notes-on-disk file-link]) ;existing file
       (re-frame/dispatch [:show-save-dialog dialog])))
   state))

(re-frame/reg-event-db
 :mnu-file-open
 (fn [state [_ dialog]]
   (.showOpenDialog dialog file-filters
                    #(re-frame/dispatch [:mnu-file-selected %]))
   state))

(re-frame/reg-event-db
 :save-notes-on-disk
 (fn [state [_ target]]
   (if target
   ;;Remove editor and output keys from each snippet in each page of the notebook
   ;;Serializes the notebook map as edn (in custom extension .nbk)
     (let [trimmer       (fn [snippets]
                           (map #(dissoc % :editor :output) snippets))
           slim-pages  (mapv
                        #(assoc % :snippets (trimmer (:snippets %)))
                        (get-in state [:notebook :pages]))
           slim-notes  (:notebook
                        (assoc-in state [:notebook :pages] slim-pages))]
       (.write jp target (pr-str slim-notes))
       (assoc-in state [:notebook :file-link] target))
     state)))

(re-frame/reg-event-db
 :show-save-dialog
 (fn [state [_ dialog]]
   (.showSaveDialog dialog
                    file-filters
                    #(re-frame/dispatch [:save-notes-on-disk %]))
   state))

(re-frame/reg-event-db
 :mnu-file-selected
 (fn [state [_ selected-file]]
   (let [file      (first selected-file)
         contents  (.read jp file)
         notebook  (reader/read-string contents)
         mnotebook (assoc notebook :file-link file)]
     (re-frame/dispatch [:switch-to-notebook mnotebook])
     state)))

(re-frame/reg-event-db
 :mnu-dev-floater
 (fn [state _]
   "Not used. A real floater"
   (println "floater")
   ;(.spawn cp (str "lein.bat") #js["repl"] #js{})
   (.start nrepl-server #js{} #(println %))
   state))

(re-frame/reg-event-db
 :mnu-dev-print-db
 (fn [state _]
   (.log js/console (clj->js state))
   state))

(re-frame/reg-event-db
 :on-close
 (fn [state _]
   (println "Closing app !!")
   (re-frame/dispatch [:quit-nrepl])
   state))
