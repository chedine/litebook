(ns litebook.views.renderer
  (:require [re-frame.core :as re-frame]
            [cljs.pprint :as pp :refer [pprint]]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as reagent]))

(defn display-success
  [value]
  (fn [value]
    [:div {:class "eval-success"}
     [:pre {:class "eval-success"} (with-out-str (pprint value))]]))

(defn display-error
  [err]
  (fn [results]
    [:div
     [:div {:class "err-source"}
      [:pre {:class "error"} (get-in results [:error :message])]]
     [:div {:class "error err-reason"} (get-in results [:error :cause])]]))

(defn display-results
  [results]
  (let [success?   (:success? results)]
    (if success?
      [display-success (:value results)]
      [display-error results])))
