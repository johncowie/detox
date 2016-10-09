(ns detox.translate
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- apply-to-vals [m f]
  (if (map? m)
    (into {} (map (fn [[k v]] [k (apply-to-vals v f)]) m))
    (f m)))

(defn flatten-map [m]
  (into {}
        (mapcat (fn [[k v]]
                  (if (map? v)
                    (map (fn [[k2 v2]] [(apply vector k k2) v2]) (flatten-map v))
                    [[[k] v]])) m)))

(defn- wrap-translations [translations f]
  (apply-to-vals translations f))

(defn- prepare-constraints [c]
  (into {} (map (fn [[k v]] [(str "~~" (name k) "~~") (str v)]) c)))

(defn- tilda-interp [v]
  (fn [value constraints]
    (let [c (prepare-constraints constraints)]
      (-> v
          (str/replace #"~value~" (str value))
          (str/replace #"~~[a-zA-Z\-_]+~~" #(get c % ""))))))

(defn- translate-error [translations messages {:keys [type value constraints]}]
  (if-let [translation-f (get-in translations type)]
    (assoc-in messages type (translation-f value constraints))
    messages))

(defn translate [errors translations]
  (let [tr (wrap-translations translations tilda-interp)]
    (reduce (partial translate-error tr) {} (:value errors))))

(defn- superfluous-translations [error-keys translation-keys]
  (sort-by str (set/difference translation-keys error-keys)))

(defn- missing-translations [error-keys translation-keys]
  (sort-by str (set/difference error-keys translation-keys)))

(defn- translation-keys [translations]
  (-> translations flatten-map keys set))

(defn check-translations [errors translations]
  (let [translation-keys (translation-keys translations)
        error-keys (->> errors :value (map :type) set)]
    {:missing     (missing-translations error-keys translation-keys)
     :superfluous (superfluous-translations error-keys translation-keys)}))

(defn- add-double-tilda [s]
  (str "~~" s "~~"))

(defn- template-message [messages {:keys [type constraints]}]
  (let [tokens (cons "~value~" (->> constraints keys (map name) sort (map add-double-tilda)))]
    (assoc-in messages type (apply str (interpose " " tokens)))))

(defn initialise-translations [errors]
  (reduce template-message {} (:value errors)))
