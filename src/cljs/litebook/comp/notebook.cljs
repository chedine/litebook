(ns litebook.comp.notebook
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [litebook.comp.pref :as pref]
              [com.rpl.specter :as s]))

(defn new-page
  []
  {:id (random-uuid)
   :name "Untitled"
   :type :page
   :snippets []})


(defn new-note
  [app-state]
  (let [timestamp (random-uuid)]
    {:name "Untitled"
     :file-link nil
     :pages [{:name "Page 1"
              :id timestamp
              :type :page
              :snippets [{:id timestamp
                          :header "Title"
                          :desc "Description goes here"
                          :code-block ""
                          :output nil
                          :editor nil
                          :marker -1
                          :cur-history 0}]}
             (pref/new-page)]
     :current-page timestamp
     :open-pages [timestamp]
     :repl       (:default-repl app-state) ; set to the defaults configured
     }))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn configured-REPL
  [app-state]
  (let [repl-pref  (:repl-config (pref/get-preferences (:notebook app-state)))]
    ((:repl repl-pref) (:repls app-state))))

(defn find-page-in-note
  [page-id notebook]
  (s/select-one* [:pages s/ALL #(= page-id (:id %))] notebook))

(defn find-page-by-id
  [state page-id]
  (s/select-one* [:notebook :pages s/ALL #(= page-id (:id %))] state))

(defn find-snippet-in-page
  [state snip-id page-id]
  (s/select-one* [:notebook :pages s/ALL #(= page-id (:id %))
                  :snippets s/ALL #(= snip-id (:id %))]
                 state))

(defn update-page-by-id
  [state page-id update-fn]
  (s/transform* [:notebook :pages s/ALL #(= page-id (:id %))] update-fn state))

(defn update-snippet-in-page
  [state snip-id page-id update-fn]
  (s/transform* [:notebook :pages s/ALL #(= page-id (:id %))
                  :snippets s/ALL #(= snip-id (:id %))]
                update-fn state))

(re-frame/reg-sub-raw
 :current-notebook
 (fn [state]
   (reaction (:notebook @state))))

(re-frame/reg-sub-raw
 :all-pages
 (fn [state]
   (reaction (:pages (:notebook @state)))))

(re-frame/reg-sub-raw
 :current-page
 (fn [state]
   (reaction (:current-page (:notebook @state)))))

(re-frame/reg-sub-raw
 :open-pages
 (fn [state]
   (reaction (:open-pages (:notebook @state)))))

(re-frame/reg-event-db
 :page-title-changed
 (fn [state [_ page-id val]]
   (update-page-by-id state page-id #(assoc % :name val))))

(re-frame/reg-event-db
 :note-title-changed
 (fn [state [_ val]]
   (assoc-in state [:notebook :name] val)))

(re-frame/reg-event-db
 :add-page
 (fn [db [_]]
   (let [pages (get-in db [:notebook :pages])
         pages-modified (concat (butlast pages)
                                [(new-page)]
                                [(last pages)])]
     (assoc-in db [:notebook :pages] pages-modified))))

(re-frame/reg-event-db
 :page-selected
 (fn [db [_ page-id]]
   (let [open-pages (get-in db [:notebook :open-pages])
         mod-pages  (if (not (in? open-pages page-id))
                      (conj open-pages page-id)
                      open-pages)]
     (re-frame/dispatch [:move-to-page page-id])
     (assoc-in db [:notebook :open-pages] mod-pages))))

(re-frame/reg-event-db
 :move-to-page
 (fn [db [_ page-id]]
   (assoc-in db [:notebook :current-page] page-id)))

(re-frame/reg-event-db
 :switch-to-notebook
 (fn [db [_ newbook]]
   (let [settings   (pref/get-preferences newbook)]
     (re-frame/dispatch [:on-repl-settings-change (:repl-config settings)])
     (assoc db :notebook newbook))))
