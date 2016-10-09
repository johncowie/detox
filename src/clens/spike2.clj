(ns clens.spike2)

;; Is it possible to make the smallest units of validation building blocks
;;  the same as the largest bits?
;;  I.e. can call the validate function on any bit of the data structure?

(defn success [v]
  [:Success v])

(defn error [type v]
  [:Error v])

(def mandatory
  {:mandatory {:selector identity
               :fn (fn [v] (if (nil? v) (error :mandatory v) (success v)))
               :constraints {}
               :deps []
               :possible-errors [:mandatory]}})

(defn greater-than [limit]
  {:greater-than {:selector identity
                  :fn (fn [v] (if (> v limit) (success v) (error :greater-than v)))
                  :constraints {:limit limit}
                  :deps []
                  :possible-errors [:greater-than]
                  }}
  )