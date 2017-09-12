(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :target-path #{"../223kazuki.github.io"}
  :dependencies '[[perun "0.4.2-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                  [clj-time "0.14.0"]])

(require '[clojure.string :as str]
         '[io.perun :refer :all]
         '[pandeiro.boot-http :refer [serve]])

(task-options!
  markdown   {:out-dir ""}
  render     {:out-dir ""}
  collection {:out-dir ""}
  static     {:out-dir ""}
  tags       {:out-dir "tags"}
  paginate   {:out-dir "pages"}
  assortment {:out-dir "assorts"}
  serve      {:resource-root ""})

(deftask build
  []
  (comp (global-metadata)
        (markdown)
        (draft)
        (print-meta)
        (slug)
        (ttr)
        (word-count)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        (render :renderer 'io.github.223kazuki.post/render)
        (collection :renderer 'io.github.223kazuki.index/render :page "index.html")
        (collection :renderer 'io.github.223kazuki.posts/render :page "posts.html")
        (tags :renderer 'io.github.223kazuki.tags/render)
        (paginate :renderer 'io.github.223kazuki.paginate/render)
        (assortment :renderer 'io.github.223kazuki.assortment/render
                    :grouper (fn [entries]
                               (->> entries
                                    (mapcat (fn [entry]
                                              (if-let [kws (:keywords entry)]
                                                (map #(-> [% entry]) (str/split kws #"\s*,\s*"))
                                                [])))
                                    (reduce (fn [result [kw entry]]
                                              (let [path (str kw ".html")]
                                                (-> result
                                                    (update-in [path :entries] conj entry)
                                                    (assoc-in [path :entry :keyword] kw))))
                                            {}))))
        (static :renderer 'io.github.223kazuki.about/render :page "about.html")
        (inject-scripts :scripts #{"start.js"})
        (sitemap :out-dir "./")
        (rss :description "Hashobject blog" :out-dir "./")
        (atom-feed :filterer :original :out-dir "./")
        (print-meta)
        (target :dir #{"../223kazuki.github.io"} :no-clean true)
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve)))
