(ns litebook.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require   [litebook.views.views :as v]
              [litebook.core.state :as s]
              [litebook.frp.handlers :as h]
              [litebook.frp.subs :as subs]
              [litebook.comp.notebook :as n]
              [litebook.comp.snippet]
              [litebook.comp.repl :as repl]
              [litebook.comp.cm :as cm]
              [litebook.comp.selfhosted :as sh]
              [litebook.comp.remoterepl :as rr]
              cljsjs.codemirror
              cljsjs.codemirror.mode.clojure
              cljsjs.codemirror.keymap.emacs
              cljsjs.codemirror.keymap.vim
              cljsjs.codemirror.addon.hint.show-hint
              cljsjs.codemirror.addon.edit.closebrackets
              cljsjs.codemirror.addon.edit.matchbrackets
              cljsjs.codemirror.addon.wrap.hardwrap
              [com.rpl.specter] 
              [re-frame.core :as re-frame]
              [reagent.core :as reagent]))

;; Renderer part of this electron app.
;; Builds the menu card for this app and mounts the container render function.
(def electron      (js/require "electron"))
(def app           (.-app electron))
(def BrowserWindow (.-BrowserWindow electron))

(def remote (.-remote electron))
(def dialog (.-dialog remote))
(def menu (.-Menu remote))
(def jp (js/require "fs-jetpack"))
(def ipcRenderer (.-ipcRenderer electron))

(def menu-card [
                {:label "File"
                 :submenu [{:label "New"
                            :accelerator "CmdOrCtrl+n"
                            :id "1"
                            :click #(re-frame/dispatch [:mnu-file-new])}
                           {:label "Open"
                            :accelerator "CmdOrCtrl+o"
                            :id "2"
                            :click #(re-frame/dispatch [:mnu-file-open dialog] )}
                           {:label "Save"
                            :accelerator "CmdOrCtrl+s"
                            :id "3"
                            :click #(re-frame/dispatch [:mnu-file-save dialog])}
                           {:label "Save As"
                            :accelerator "CmdOrCtrl+Alt+s"
                            :id "4"
                            :click #(re-frame/dispatch [:show-save-dialog dialog])}]}
                {:label "Notes"
                 :submenu [{:label "New Page"
                            :accelerator "Alt+p"
                            :id "1"
                            :click #(re-frame/dispatch [:add-page])}
                           {:label "New Snippet"
                            :accelerator "Alt+s"
                            :id "2"
                            :click #(re-frame/dispatch [:add-snippet])}
                           {:label "Eval all"
                            :accelerator "Alt+3"
                            :id "5"
                            :click #(re-frame/dispatch [:eval-all])}]}

                {:label "Dev"
                 :submenu [{:label "Print-DB"
                            :accelerator "CmdOrCtrl+2"
                            :id "1"
                            :click #(re-frame/dispatch [:mnu-dev-print-db])}
                           {:label "Floater"
                            :accelerator "CmdOrCtrl+3"
                           :id "2"
                           :click #(re-frame/dispatch [:mnu-dev-floater])}
                           {:label "DevTools"
                            :accelerator "CmdOrCtrl+Shift+I"
                            :id "2"
                            :click (fn [i,fw] (.toggleDevTools (.-webContents fw)))}]}
                ])
(defn find-mnu-item
  [lbl menu-state]
  (filter #(= (.-label %) lbl) menu-state))

(defn mount-root []
(.log js/console "hi")
 (reagent/render [v/container]
                (.getElementById js/document "app")))

(defn init!
  []
  (.setApplicationMenu menu (.buildFromTemplate menu (clj->js menu-card)))
  (enable-console-print!)
  (re-frame/dispatch-sync [:initialize-db])
  (re-frame/dispatch-sync [:init-app])
  (mount-root))

;Register exit handlers from the renderer
(set! (.-onbeforeunload js/window)
      (fn [ev]
        (repl/cleanup nil)))

(init!)