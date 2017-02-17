(ns litebook.core.config)

(def on-windows?
  (.test #"^win" js/process.platform))

(defn testy
  []
  true)

(defn testy1
    []
    false)
