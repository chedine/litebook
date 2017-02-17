(ns litebook.comp.cm
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent]
            [litebook.comp.repl :as repl]
            [litebook.comp.selfhosted :as s]
            [litebook.comp.notebook :as notes]))


(def cm-opts {:autofocus true
              :mode "clojure"
              :keyMap "emacs"
              :theme "3024-day"
              :matchBrackets true
              :autoCloseBrackets true
              :lineNumbers false})

(defn new-console
  "return a new codemirror editor"
  [el editor-opts]
  (js/CodeMirror. el (clj->js editor-opts)))

(defn extract-word
  [line cursor]
  (let [valid-delim? #(or (= " " %) (= "(" %) (= ")" %))
        spos    (loop [p (dec cursor)]
                  (if (or (= p 0)
                          (valid-delim? (.charAt line p)))
                    p
                    (recur (dec p))))
        epos   (loop [p cursor]
                 (if (or (>= p (dec (count line)))
                         (valid-delim? (.charAt line p)))
                   p
                   (recur (inc p))))]
    ;substring the word from one char next to spos
    [[(inc spos) epos] (subs line (inc spos)  epos)]))

(defn pop-suggestions
  [start end suggestions cb]
  "A wrapper around the actual callback function to carry some more contextual info
Start - end => A range in the code mirror editor which will be replaced with the
selected suggestion
Suggestions - A list of suggestions
cb - Actual callback used by code mirror"
  (cb (clj->js {:from start :to end :list suggestions})))

(defn autocomplete
  [cm suggest-fn cb]
  "Invoked everytime Ctrl+Space is pressed while within the editor.
Entire autocomplete process is configured to be async (because nrepl)
cm - Code mirror instance
suggest-fn - A function that will be used to get the suggestions.
Different REPLs has its own style of finding the suggestions
cb - A callback function to b invoked when suggestions are ready"
  (let  [cursor      (.getCursor cm)
         line        (aget cursor "line")
         word-pos    (extract-word (.getLine cm line) (aget cursor "ch"))
         start       (js-obj "line" line "ch" (first (first word-pos)))
         end         (js-obj "line" line "ch" (second (first word-pos)))]
    (suggest-fn (second word-pos)
                #(pop-suggestions start end % cb))))

(re-frame/reg-event-db
 :init-console
 (fn [db [_ snippet page el default-opts]]
   "Inititializes the console/cm editor with app wide settings."
   (let [id           (:id snippet)
         page-id      (:id page)
         init-content (:code-block snippet)
         opts         (assoc default-opts
                             :extraKeys {"Ctrl-Enter"
                                         #(re-frame/dispatch [:eval id page-id])
                                         "Ctrl-Up"
                                         #(re-frame/dispatch [:pop-eval-history id page-id])
                                         "Ctrl-k" #(println "test")
                                         "Ctrl-Space" "autocomplete"})
         cm-inst      (new-console el opts)]
     (when-not (:autocomplete-configd? db)
       (re-frame/dispatch [:configure-codecomplete])) ;todo: efx handlers
     (.setValue cm-inst init-content)
     (notes/update-snippet-in-page db id page-id
                                   #(assoc % :editor cm-inst)))))

(re-frame/reg-event-db
 :configure-codecomplete
 (fn [state _]
   "Event is fired when the app state is loaded
and whenever user switches to different REPL."
   (set! (.. js/CodeMirror -hint -clojure)
         (fn [cm cb]
           (autocomplete cm (:suggest-fn (notes/configured-REPL state)) cb)))
   (set! (.. js/CodeMirror -hint -clojure -async) true)
   (assoc state :autocomplete-configd? true)))

(re-frame/reg-event-db
 :pop-eval-history
 (fn [db [_ snip-id page-id]]
   "Invoked when ctrl+up is pressed. To pop the recent form from history"
   (let [card    (notes/find-snippet-in-page db snip-id page-id)
         editor  (:editor card)
         doc     (.getDoc editor)
         cursor  (.getCursor doc)
         cline   (aget cursor "line")
         line    (.getLine doc cline)
         pos     (js-obj "line" cline "ch" 0)
         clean?  (.isClean doc (:marker card));if editor state have changed
         history (:history db)
         max-ref (dec (count history))
         item-no (if clean? (:cur-history card) 0)
         data    (str (nth history item-no))]
     (.execCommand editor "deleteLine")
     (.replaceRange doc data pos)
     (notes/update-snippet-in-page db snip-id page-id
                        #(assoc %
                                :cur-history (if (= item-no max-ref)
                                               0
                                               (inc item-no))
                                :marker (.changeGeneration doc))))))
