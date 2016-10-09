(ns clens.spike
  (:require [com.stuartsierra.dependency :as dep]))

;; REQ
;;  - should abstract the mechanism for value selection, and be able to modify with wrapping function
;;  X enumeration of possible errors
;;  X decouple messages from validations
;;  - can validate multiple foci with single validator
;;  - short-circuiting a validation pipeline i.e. for 'optional'
;;  - add support for left & right results (success and failure)
;;  X specify dependencies on other validations
;;  X validate combination of fields (i.e. how they relate to each other)
;;  - check that validators have same number of args as selectors
;;  - validators should compose
;;  - schema validation for validators  (or clojure.spec?)
;;  - different stages of validation?
;;  - should parsing be performed in conjunction?

;; - If validations were combined to make new validations, that hierarchy could be stored in a tree of error types?


(defmacro defv [name args & body]
  (let [[a1 & r] args]
    `(defn ~name [~@r]
       {:error       ~(keyword name)
        :constraints ~(zipmap (map keyword r) r)
        :fn          (fn [~a1] ~@body)})))

(declare greater-than mandatory x>y)

(defv greater-than [x limit]
      (> x limit))

(defv mandatory [x]
      (not (nil? x)))

(defv x>y [[x y]]
      (> x y))

(macroexpand-1 '(defv x>y [[x y]] (> x y)))

;(macroexpand-1 '(defv mandatory [x] (not (nil? x))))

#_(defn greater-than [limit]
  {:error       :greater-than
   :constraints {:limit limit}
   :fn          (fn [v] (> v limit))})

#_(def x>y
  {:error       :x>y
   :constraints {}
   :fn          (fn [x y] (> x y))})

#_(def mandatory
  {:error       :mandatory
   :constraints {}
   :fn          (fn [v] (not (nil? v)))})

(defn apply-validator [x v]
  (when-not ((:fn v) x)
    (-> v (assoc :val x) (dissoc :fn))))

(defn validate-target [x vs]
  (some (partial apply-validator x) vs))



(validate-target 3 [(mandatory) (greater-than 5)])

;;

;; used like this: [(greater-than 5)]

(def a-selector :a)
(def b-selector :b)

(def test-validations
  {:vAB [[b-selector a-selector] [(x>y)] [:vA :vB]]
   :vA  [a-selector              [(mandatory) (greater-than 12)]]
   :vB  [b-selector              [(mandatory) (greater-than 10)]]
   })

(defn add-validation-to-graph [graph [k [selectors validations deps]]]
  (reduce #(dep/depend %1 k %2) graph deps))

(defn sort-dependencies [validations]
  (-> (reduce add-validation-to-graph (dep/graph) validations)
      (dep/topo-sort)))

(defn deps-errored? [existing-errors deps]
  (->> existing-errors
       keys
       (some (set deps))))

(defn select-vals [data selectors]
  ;(prn data selectors)
  (if (sequential? selectors)
    (map #(% data) selectors)
    (selectors data)))

(defn apply-validation-chain [data existing-errors [error-key [selectors validations deps]]]
  (if (deps-errored? existing-errors deps)
    existing-errors
    (let [vals (select-vals data selectors)]
      (if-let [err (validate-target vals validations)]
        (assoc existing-errors error-key err)))))

(defn apply-validations [validations data]
  (reduce (partial apply-validation-chain data) nil validations))

(defn position-in-seq [sequence]
  (fn [x]
    (.indexOf sequence x)))

(defn order-validations [validations]
  (let [sequence (sort-dependencies validations)]
    (sort-by (comp (position-in-seq sequence) first) validations)))

(defn run-validations [data validations]
  (-> validations
      order-validations
      (apply-validations data)))

(run-validations {:a 10 :b 10} test-validations)

(defn validation-chain-possible-errors [[error-key [selectors validations deps]]]
  [error-key (map :error validations)])

(defn possible-errors
  "Find all possible errors for a set of validations"
  [test-validations]
  (->> test-validations
       (map validation-chain-possible-errors)
       (into {})))

(possible-errors test-validations)

(defn -main [& args]
  (prn "Started"))

;;; (chain
;;;    (parallel (chain :blah v1 v2 v3) (chain :blob v2 v3 v4))
;;; (chain :blah v1 v2 v3)



