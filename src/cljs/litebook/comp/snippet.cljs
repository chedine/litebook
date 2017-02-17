(ns litebook.comp.snippet
  (:require [re-frame.core :as re-frame]
            [litebook.comp.repl :as r]
            [litebook.comp.pref :as pref]
            [litebook.comp.notebook :as notes]))

;;used when the repl is loading when the user tries to eval a form
(def blank-results {:success? false
                    :value nil
                    :error {:cause "Repl is not connected yet."
                            :message "Repl is not connected yet."}})
(defn eval-form
  [snip-id page-id contents repl]
  "Triggered by pressing ctrl+enter while within a console.
   Passes the content to the repl/evaluator
snip-id -> ID of the snippet to be evaluated
page-id -> Owner page of the snippet
form    -> Form to be evaluated
repl    -> REPL configured for the notebook"
  ;; repl will have a :eval-fn , a function that can eval this form
  (let  [print-fn  (fn [results]
                     (re-frame/dispatch [:print-eval snip-id page-id results]))
         eval-fn   (fn[form]
                     ((:eval-fn repl) print-fn (str form)))]
    (doall (map eval-fn (r/read-all contents)))))

(defn new-snippet
  []
  {:id (.now js/Date)
   :header ""
   :desc ""
   :code-block ""
   :output nil
   :marker -1
   :cur-history 0})

(re-frame/reg-event-db
 :add-snippet
 (fn [db [_ page-id]]
   (let [_ (println page-id)
         pid      (if page-id
                    page-id (get-in db [:notebook :current-page]))
         page     (notes/find-page-by-id db pid)
         snippets  (:snippets page)
         mod-snips (conj snippets (new-snippet))]
     ( notes/update-page-by-id db pid #(assoc % :snippets mod-snips)))))

(re-frame/reg-event-db
 :snip-title-changed
 (fn [db [_ val snippet page]]
   (let [sid (:id snippet)
         pid (:id page)]
     (notes/update-snippet-in-page
      db sid pid #(assoc % :header val)))))

(re-frame/reg-event-db
 :snip-desc-changed
 (fn [db [_ val snippet page]]
   (let [sid (:id snippet)
         pid (:id page)]
     (notes/update-snippet-in-page
      db sid pid #(assoc % :desc val)))))

(re-frame/reg-event-db
 :eval-all
 (fn [db _]
   (let  [cur-pid  (get-in db [:notebook :current-page])
          cur-page (notes/find-page-by-id db cur-pid)
          snippets (:snippets cur-page)
          evalfn   (fn [snippet]
                     (re-frame/dispatch [:eval (:id snippet) cur-pid]))]
     (println (str "Evaluating " snippets))
     (doall (map evalfn snippets))
     db)))

(re-frame/reg-event-db
 :eval
 (fn [db [_ snippet-id page-id]]
   "Triggered by pressing ctrl+enter while within a console.
     Passes the content of the editor for code eval ."
   (let [snippet    (notes/find-snippet-in-page db snippet-id page-id)
         editor     (:editor snippet)
         repl       (notes/configured-REPL db)
         settings   (pref/get-preferences (:notebook db))
         connected? (= :connected (get-in settings [:repl-config :repl-state]))]
     (if connected?
       (eval-form snippet-id page-id (.getValue editor) repl)
       (re-frame/dispatch [:publish-eval-results snippet-id page-id blank-results]))
     (notes/update-snippet-in-page
      db snippet-id page-id #(assoc % :code-block (.getValue editor))))))

(re-frame/reg-event-db
 :print-eval
 (fn [db [_ snip-id page-id results]]
   "Default print handler to handle printing/rendering eval results.
     Card id can be -1, in which case the results are discarded. Useful
     for injecting forms in the user ns (like a default requires)"
   (re-frame/dispatch [:add-eval-history results])
   (if (< snip-id 0)
     db
     (let [snippet (notes/find-snippet-in-page db snip-id page-id)
           repl    (notes/configured-REPL db)
           output  ((:formatter repl) results)]
       (re-frame/dispatch [:publish-eval-results snip-id
                           page-id output])
       db))))

(re-frame/reg-event-db
 :publish-eval-results
 (fn [db [_ snip-id page-id results]]
   (notes/update-snippet-in-page
    db snip-id page-id #(assoc % :output results))))

(re-frame/reg-event-db
 :add-eval-history
 (fn [db [_ eval-result]]
   ""
   (let [history (:history db)
         to-add  (str (:form eval-result))
         added?  (not (empty? (filter #(= % to-add) history)))]
     (if added? db
         (update-in db [:history] conj (str (:form eval-result)))))))
