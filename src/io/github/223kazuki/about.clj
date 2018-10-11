(ns io.github.223kazuki.about
  (:require [io.github.223kazuki.common :refer [layout]]))

(defn render [{global-meta :meta posts :entries :as config}]
  (layout config
          [:div.about.fit
           [:p.h1 "About"]
           [:ul
            [:li "Kazuki Tsutsumi / 堤 一樹"]
            [:li "Clojure Engineer"]]
           [:p.h2 "Career"]
           [:table.col-12
            [:tr
             [:td "May/2017 - Now"]
             [:td "Senior System Engineer @ San Jose, CA"]]
            [:tr
             [:td "Apr/2012 - May/2017"]
             [:td "System Engineer @ Tokyo"]]
            [:tr
             [:td "Apr/2012 - Now"]
             [:td "General Manager @ Kokugakuin University Sumo Club / 國學院大學相撲部監督"]]
            [:tr
             [:td "Apr/2010 - Mar/2012"]
             [:td "Tokyo Institute of Technology (Astrophysics)"]]
            [:tr
             [:td.col-3 "Apr/2006 - Mar/2010"]
             [:td "Nagoya University (Astronomy)"]]]
           [:p.h2 "SNS Accounts"]
           [:ul
            [:li "Twitter: " [:a {:href "//twitter.com/goronao"} "@goronao"]]
            [:li "GitHub: " [:a {:href "//github.com/223kazuki"} "223kazuki"]]
            [:li "Slide Share: " [:a {:href "//www.slideshare.net/ssuser8b0ea4"} "Kazuki Tsutsumi"]]]
           [:p.h2 "Skills/Interests"]
           [:ul
            [:li "Clojure / ClojureScript"]
            [:li "Web Development"]
            [:li "Java"]
            [:li "JavaScript"]
            [:li "React.js"]
            [:li "Rust"]
            [:li "Blockchain"]]]))
