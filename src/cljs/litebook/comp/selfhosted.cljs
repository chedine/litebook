(ns litebook.comp.selfhosted
  (:require [replumb.core :as replumb]
            [replumb.load :as load]
            [replumb.repl :as repl]
            [replumb.browser :as browser]
            [replumb.ast :as ast]
            [cljs.tools.reader.reader-types :as rt]
            [cljs.tools.reader :as rdr]))

(defn eval
  [printer content]
  "Given a form, and a print function, evaluates the form in current ns
and returns the result as a json value"
  (let [repl-opts
        (assoc browser/default-opts :load-fn! load/fake-load-fn! :no-pr-str-on-value true)]
    (replumb/read-eval-call repl-opts printer content)))

(defn compare-completion
  "The comparison algo for completions
  1. if one is exactly the text, then it goes first
  2. if one *starts* with the text, then it goes first
  3. otherwise leave in current order
  Source : https://github.com/jaredly/reepl
  "
  [text a b]
  (cond
    (and (= text a)
         (= text b)) 0
    (= text a) -1
    (= text b) 1
    :else
    (let [a-starts (= 0 (.indexOf a text))
          b-starts (= 0 (.indexOf b text))]
      (cond
        (and a-starts b-starts) 0
        a-starts -1
        b-starts 1
        :default 0))))

(defn compare-ns
  "Sorting algo for namespaces
  The current ns comes first, then cljs.core, then anything else
  alphabetically"
  [current ns1 ns2]
  (cond
    (= ns1 current) -1
    (= ns2 current) 1
    (= ns1 'cljs.core) -1
    (= ns2 'cljs.core) 1
    :default (compare ns1 ns2)))

(defn get-from-js-ns
  "Use js introspection to get a list of interns in a namespaces
  This is pretty dependent on cljs runtime internals, so it may break in the
  future (although I think it's fairly unlikely). It takes advantage of the fact
  that the ns `something.other.thing' is available as an object on
  `window.something.other.thing', and Object.keys gets all the variables in that
  namespace."
  [ns]
  (let [parts (map munge (.split (str ns) "."))
        ns (reduce aget js/window parts)]
    (if-not ns
      []
      (map demunge (js/Object.keys ns)))))

(defn dedup-requires
  "Takes a map of {require-name ns-name} and dedups multiple keys that have the
  same ns-name value."
  [requires]
  (first
   (reduce (fn [[result seen] [k v]]
             (if (seen v)
               [result seen]
               [(assoc result k v) (conj seen v)])) [{} #{}] requires)))

(defn get-matching-ns-interns [[name ns] matches? only-ns]
  (let [ns-name (str ns)
        publics (keys (ast/ns-publics @replumb.repl/st ns))
        publics (if (empty? publics)
                  (get-from-js-ns ns)
                  publics)]
    (if-not (or (nil? only-ns)
                (= only-ns ns-name))
      []
      (sort (map #(symbol name (str %))
                 (filter matches?
                         publics))))))

(defn cljs-completion
  "Tab completion. Copied w/ extensive modifications from replumb.repl/process-apropos.
  https://github.com/jaredly/reepl"
  [text cb]
  (let [[only-ns text] (if-not (= -1 (.indexOf text "/"))
                         (.split text "/")
                         [nil text])
        matches? #(and
                   ;; TODO find out what these t_cljs$core things are... seem to be nil
                   (= -1 (.indexOf (str %) "t_cljs$core"))
                   (< -1 (.indexOf (str %) text)))
        current-ns (replumb.repl/current-ns)
        replace-name (fn [sym]
                       (if (or
                            (= (namespace sym) "cljs.core")
                            (= (namespace sym) (str current-ns)))
                         (name sym)
                         (str sym)))
        requires (:requires
                  (ast/namespace @replumb.repl/st current-ns))
        only-ns (when only-ns
                  (or (str (get requires (symbol only-ns)))
                      only-ns))
        requires (concat
                  [[nil current-ns]
                   [nil 'cljs.core]]
                  (dedup-requires (vec requires)))
        names (set (apply concat requires))
        defs (->> requires
                  (sort-by second (partial compare-ns current-ns))
                  (mapcat #(get-matching-ns-interns % matches? only-ns))
                  ;; [qualified symbol, show text, replace text]
                  (map #(-> [% (str %) (replace-name %) (name %)]))
                  (sort-by #(get % 3) (partial compare-completion text)))
        suggestions (vec (concat (take 75 defs)
                                 (map #(-> [% (str %) (str %)])
                                      (filter matches? names))))]
    (cb (filter #(not (nil? %)) (mapv first suggestions)))))

(defn format-results
  [results]
  {:success? (:success? results)
   :value    (:value results)
   :error    (if (:success? results)
               {}
               {:cause (.-message (.-cause (:error results)))
                :message (.-message (:error results))})})
(defn make
  [spec]
    {:session  nil
     :eval-fn  eval
     :formatter format-results
     :suggest-fn cljs-completion})
