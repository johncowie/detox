(ns detox.traversy
  (:require [detox.core :as c]
            [traversy.lens :as l]))

(deftype TraversySelection []
  c/Selection
  (select-vals [this x selector]
    (l/view x selector))
  (update-vals [this x selector f]
    (l/update x selector f)))

(def traversy-selection (TraversySelection. ))

(defn at [validator id & selectors]
  (-> (apply c/at validator id selectors)
      (c/with-selection traversy-selection)))
