(ns detox.core
  (:require [detox.util :refer [throw-error]]))

(defn result [type value]
  {:result type :value value})

(def success-value (partial result :success))
(def short-circuit-value (partial result :break))
(def error-value (partial result :error))

(def result-value :value)

(def short-circuit? (comp (partial = :break) :result))
(def error-value? (comp (partial = :error) :result))
(def success? (comp (partial = :success) :result))

(defn error-result [errors]
  errors)

(defn core-error
  ([type value constraints]
   {:type        type
    :constraints (or constraints {})
    :value       value})
  ([type value] (core-error type value {})))

(def empty-error-result
  (error-value []))

(defn single-error-result [type value constraints]
  (error-value [{:type type :value value :constraints constraints}]))

(defn prefix-error [id error]
  (update error :type #(cons id %)))

(defn prefix-errors [errors-result id]
  (update errors-result :value #(map (partial prefix-error id) %)))

;; Rewrite as a monadic do-hickey
(defn mappend [a b]
  (case [(success? a) (success? b)]
    [true true] b
    [true false] b
    [false true] a
    [false false] (update a :value concat (:value b))))

(defn combine-errors [error-results]
  (reduce mappend empty-error-result error-results))

(defprotocol Selection
  (select-vals [this x selector])
  (update-vals [this x selector f]))

(deftype PathSelection []
  Selection
  (select-vals [this x selector]
    [(get-in x selector)])
  (update-vals [this x selector f]
    (update-in x selector f)))

(defprotocol Validator
  (-validate [this x])
  (possible-errors [this]))

(defrecord NestedValidator [id selectors validator selection]
  Validator
  (-validate [this x]
    (if (= (count selectors) 1)
      (let [error-state (atom [])
            update-fn (fn [v] (let [r (-validate validator v)]
                                (swap! error-state conj r)
                                (result-value r)))
            updated (update-vals selection x (first selectors) update-fn)]
        (if (every? success? @error-state)                             ;; need some sort of fmap affair here
          (success-value updated)
          (prefix-errors (combine-errors @error-state) id)))
      (let [vals (map (comp first #(select-vals selection x %)) selectors)
            r (-validate validator vals)]
        (if (success? r)
          (success-value (reduce (fn [vv [s x]] (update-vals selection vv s (constantly x))) x (map vector selectors (result-value r))))
          (prefix-errors r id)))))
  (possible-errors [this]
    (-> (possible-errors validator)
        (prefix-errors id))))

(defn bind [m validator]
  (if (or (error-value? m) (short-circuit? m))
    m
    (-validate validator (result-value m))))

(deftype ChainValidator [validators]
  Validator
  (-validate [this x]
    (let [r (reduce bind (success-value x) validators)]
      (if (short-circuit? r) (success-value (result-value r)) r)))
  (possible-errors [this]
    (combine-errors (map possible-errors validators))))

(defn- accumulate-validations [{:keys [last-success result]} validator]
  (let [r (-validate validator (result-value last-success))
        combined (mappend result r)]
    (if (success? r)
      {:last-success r :result combined}
      {:last-success last-success :result combined})))

(deftype GroupValidator [validators]
  Validator
  (-validate [this x]
    (let [initial {:last-success (success-value x)
                   :result       (success-value x)}]
      (:result (reduce accumulate-validations initial validators))))
  (possible-errors [this]
    (combine-errors (map possible-errors validators))))

(defrecord ValueValidator [id validate-f constraints no-error]
  Validator
  (-validate [this x]
    (let [result (validate-f x)]
      (if (error-value? result)
        (single-error-result [id] x constraints)
        result)))
  (possible-errors [this]
    (if no-error
      empty-error-result
      (single-error-result [id] "...value..." constraints))))

(defn validate [x validator]
  (-validate validator x))

(defn chain [& validators]
  (ChainValidator. validators))

(defn group [& validators]
  (GroupValidator. validators))

(defn with-selection [validator selection]
  (assoc validator :selection selection))

(defn at
  [validator id & selectors]
  (NestedValidator. id selectors validator (PathSelection.)))

(defn base-validator
  ([id validate-f constraints no-error]
   (if (and id validate-f)
     (ValueValidator. id validate-f constraints no-error)
     (throw-error "Not a valid unit validator")))
  ([id validate-f constraints]
   (base-validator id validate-f constraints false)))

(defn validator? [x]
  (satisfies? Validator x))

(deftype CollectionValidator [validator]
  Validator
  (-validate [this x]                                       ;; TODO check that x is a collection
    (let [rs (map (partial -validate validator) x)
          vs (map result-value rs)]
      (if (every? success? rs)
        (success-value vs)
        (error-value (apply concat (map result-value (remove success? rs)))))))
  (possible-errors [this]
    (possible-errors validator)))

(defn each-is [validator]
  (CollectionValidator. validator))

(defn from-map
  ([m]
   (cond
     (validator? m) m
     (sequential? m) (each-is (from-map (first m)))         ;; TODO verify that only one validator in list
     :else (apply group (map (fn [[k v]] (at (from-map v) k [k])) m)))))

