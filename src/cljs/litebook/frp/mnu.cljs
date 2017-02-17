(ns litebook.frp.mnu
  (:require [re-frame.core :as re-frame]
            [litebook.core.state :as s]))

(re-frame/reg-event-db
 :mnu-file-new
 (fn  [db _]
   (.log js/console "clicked")
   (assoc db :name "Name changed!!")))
