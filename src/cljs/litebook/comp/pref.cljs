(ns litebook.comp.pref
  (:require [com.rpl.specter :as s]
            [reagent.ratom :refer [reaction]]
            [re-frame.core :as re-frame]))

(defn new-page
  []
  {:id   (random-uuid)
   :name "Preferences"
   :type :pref
   :repl-config {:repl       :self
                 :nrepl-port 7766
                 :repl-state :connected}})

(defn get-preferences
  [notebook]
  (s/select-one* [:pages s/ALL #(= :pref (:type %))] notebook))

(defn update-preferences
  [state update-fn]
  (s/transform* [:notebook :pages s/ALL #(= :pref (:type %))] update-fn state))

(re-frame/reg-sub-raw
 :settings
 (fn [state]
   (reaction (last (:pages (:notebook @state))))))

(re-frame/reg-event-db
 :save-pref
 (fn [state [_ pref]]
   (let [settings      (get-preferences (:notebook state))
         getrepl       #(get-in % [:repl-config :repl])
         repl-changed? (not (= (getrepl settings) (getrepl pref)))]
     (when repl-changed?
       (re-frame/dispatch [:on-repl-settings-change pref]))
     state)))

(re-frame/reg-event-db
 :on-repl-settings-change
 (fn [state [_ pref]]
   (let [mpref      (assoc pref :repl-state :connecting)
         repl       (:repl pref)
         port       (:port pref)]
     (cond
       (= repl :self)      (re-frame/dispatch [:switch-to-self])
       (= repl :remote)    (re-frame/dispatch [:switch-to-nrepl-port port])
       (= repl :self-clj)  (re-frame/dispatch [:prep-nrepl-server port]))
     (update-preferences state #(assoc % :repl-config mpref)))))

