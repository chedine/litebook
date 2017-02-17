(ns litebook.core.state)

(def app-state
  {:name "Lite-Book"
   :notebook nil
   :default-repl :self
   :editor-config {:autofocus true
                   :mode "clojure"
                                        ;              :keyMap "vim"
                   :theme "3024-day"
                   :matchBrackets true
                   :autoCloseBrackets true
                   :lineNumbers false}
   :repls      {}
   :autocomplete-configd? false
   :history '()
   :nrepl-config nil})
