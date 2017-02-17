(ns litebook.views.views
  (:require [re-frame.core :as re-frame]
            [reagent.core :as reagent :refer [atom]]
            [litebook.comp.notebook :as notes]
            [litebook.comp.pref :as pref]
            [litebook.comp.repl :as repl]
            [litebook.views.renderer :as r]))

(defn validate-repl-form
  [repl-config]
  (or
   (and
    (not (= (:repl repl-config) :self))
    (not (clojure.string/blank? (:nrepl-port repl-config))))
   (= (:repl repl-config) :self)))

(defn repl-modeline
  []
  (let [settings  (re-frame/subscribe [:settings])]
    (fn []
      (let  [_ (println @settings)
             repl       (get-in @settings [:repl-config :repl])
             status     (get-in @settings [:repl-config :repl-state])
             loading?   (= status :connecting)]
        [:div
         [:span {:class "repl-status"} (status repl/repl-status-map)]
         [:span {:class "repl-to"} " To "]
         [:span {:class "repl-type"} (repl repl/repl-map)]
         (when loading?
           [:i {:class "fa fa-cog fa-spin fa-2x fa-fw"}])]))))

(defn console-comp
  [snippet page conf]
  (reagent/create-class
   {:reagent-render
    (fn []
      [:div {:class "cmirror"}])
    :component-did-mount
    (fn [this]
      (re-frame/dispatch
       [:init-console snippet page (reagent/dom-node this) conf]))}))

(defn add-snippet-bar
  [page]
  (fn [page]
    [:div
     [:button {:class "btn-toggle button"
               :on-click #(re-frame/dispatch [:add-snippet (:id page)])}
      [:i {:class "fa fa-plus"}]]]))

(defn snippet-view
  [snippet page]
  (let  [editor-config  (re-frame/subscribe [:editor-config])
         change-handler (fn [e]
                          #(re-frame/dispatch [e (-> % .-target .-innerHTML)
                                               snippet page]))]
    (fn [snippet page]
      [:div
       [:h2 {:contentEditable true
             :on-blur (change-handler :snip-title-changed)
             :dangerouslySetInnerHTML #js {:__html (:header snippet)}
             :placeholder "Title .."}]
       [:p {:contentEditable true
            :on-blur (change-handler :snip-desc-changed)
            :dangerouslySetInnerHTML #js {:__html (:desc snippet)}}]
       [:div {:class "coder"}
        [console-comp snippet page @editor-config]
        [r/display-results (:output snippet)]]])))

(defn show-pref
  [cur-page]
  (let  [repl-conf (atom (:repl-config cur-page))
         update-fn (fn [atomvar path val]
                     (swap! atomvar assoc-in path val))
         message   (atom nil)
         save-fn   (fn [conf]
                     (if (validate-repl-form @repl-conf)
                       (do
                         (reset! message "Settings Saved ..")
                         (re-frame/dispatch [:save-pref @repl-conf]))
                       (reset! message "Error !!")))
         reset-fn  #(reset! repl-conf (:repl-config cur-page))]
    (fn []
      (let [repl       (:repl @repl-conf)
            show-port? (= repl :self)]
        [:div
         (when @message
           [:div {:class "messages"}] @message)
         [:div {:class "control "}
          [:div
           [:label {:class "label"} "REPL Settings"]]
          [:div {:class "control has-addons"}
           [:select {:class "input"
                     :value (if repl (name repl) "")
                     :on-change #(update-fn repl-conf
                                            [:repl]
                                            (keyword (-> % .-target .-value)))}
            [:option {:value "self"} "Self Hosted -CLJS"]
            [:option {:value "remote"} "Remote nREPL"]
            [:option {:value "self-clj"} "Self Hosted nREPL"]]
           [:input {:type "text"
                    :placeholder "Port"
                    :class "input"
                    :value (:nrepl-port @repl-conf)
                    :disabled show-port?
                    :on-change #(update-fn repl-conf
                                           [:nrepl-port]
                                           (-> % .-target .-value))}]]]
         [:p {:class "control"}
          [:button {:class "button is-primary"
                    :on-click save-fn} "Save"]
          [:button {:class "button"
                    :on-click reset-fn} "Reset"]]]))))

(defn show-page
  [cur-page]
  (fn [cur-page]
    [:div
     (for [snippet (:snippets cur-page)]
       ^{:key (str "cp_" (:id cur-page) "_s_" (:id snippet))}
       [snippet-view snippet cur-page])
     [add-snippet-bar cur-page]]))

(defn page-title
  [page]
  (fn [page]
    [:div {:class "page-title"}
     [:center
      [:h1 {:contentEditable true
            :on-blur #(re-frame/dispatch [:page-title-changed
                                          (:id page)
                                          (-> % .-target .-innerHTML)])
            :dangerouslySetInnerHTML #js {:__html (:name page)}}]]]))

(defn page-tabs
  [notebook]
  (fn [notebook]
    (let [open-pages  (:open-pages notebook)
          cur-page    (:current-page notebook)]
      [:div {:class "tabs is-boxed"}
       [:ul
        (for [open-page open-pages]
          (let [page (notes/find-page-in-note open-page notebook)]
            ^{:key (str "tab_p_" open-page)}
            [:li {:class (if (= open-page cur-page) "is-active" "")
                  :on-click #(re-frame/dispatch [:move-to-page open-page])}
             [:a
              [:span {:class "icon is-small"}
               [:i {:class "fa fa-file-text-o"}]]
              [:span (:name page)]]]))]])))

(defn page-nav
  []
  (let [selected  (re-frame/subscribe [:current-page])
        pages     (re-frame/subscribe  [:all-pages])]
    (fn []
      [:aside {:class "menu"}
       (let [selected-id  @selected]
         (for [page @pages]
           ^{:key (str "nav_p_" (:id page))}
           [:div
            [:p {:class (if (= selected-id (:id page)) "selected menu-label" "menu-label")
                 :on-click #(re-frame/dispatch [:page-selected (:id page)])}
             (:name page)]
            [:ul {:class "menu-list"}
             (for [snippet (filter
                            #(not (clojure.string/blank? (:header %)))
                            (:snippets page))]
               ^{:key (str "psh_" (:id snippet))}
               [:li
                [:a {:href "#"} (:header snippet)]])]]))])))

(defn overview
  [notebook]
  (fn [notebook]
    [:div {:class "note-overview"}
     [:h1 {:contentEditable true
           :on-blur #(re-frame/dispatch [:note-title-changed (-> % .-target .-innerHTML)])
           :dangerouslySetInnerHTML #js {:__html (:name notebook)}}]
     [:ul
      [:li [:div (str (count (:pages notebook)) " Pages")]]
      [:li [repl-modeline ]]]
     ]))

(defn full-panel
  [notebook]
  "Render function for the entire content.
Left Panel
 - Notebook Overview
 - For every page in notebook
   - List page names
     - For every snippet in page
       - List snippet title
Right Panel
 - For each page selected/open
   - Display as tabs
 - For every snippet in the current page
   - Render snippet title/desc/editor/outpt"
  (let [notebook  (re-frame/subscribe [:current-notebook])]
    (fn []
      (let [title (:name @notebook)
            pages (:pages @notebook)
            cur-page (notes/find-page-in-note (:current-page @notebook) @notebook)]
        [:div
         ;LEFT Side Nav Panel
         [:div {:class "left-pane"}
          [overview @notebook]
          [page-nav]]
         ;Right Side Content Panel
         [:div {:class "column right-pane"}
          [page-tabs @notebook]
          [page-title cur-page]
          (if (= (:type cur-page) :page)
            [show-page cur-page]
            [show-pref cur-page])]]
        ))))

(defn container
  []
  "Root view is a container. Layouts the entire view"
  (fn []
    [full-panel]))
