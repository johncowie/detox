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
  ;(error-value {})
  (error-value []))

(defn single-error-result [type value constraints]
  ;(error-value (assoc-in {} type [{:value value :constraints constraints}]))
  (error-value [{:type type :value value :constraints constraints}]))

(defn apply-to-success [result f]
  (if (success? result)
    (update result :value f)
    result))

(defn apply-to-error [result f]
  (if-not (success? result)
    (update result :value f)
    result))

(defn wrap-map [m id]
  {id m})

(defn prefix-error [id error]
  (update error :type #(cons id %)))

(defn prefix-errors [errors-result id]
  (update errors-result :value #(map (partial prefix-error id) %)))

(defn deep-merge-with [f a b]
  (if (and (map? a) (map? b))
    (merge-with (partial deep-merge-with f) a b)
    (f a b)))

;; Rewrite as a monadic do-hickey
(defn mappend [a b]
  (update a :value concat (:value b)))

(defn combine-errors [error-results]
  (reduce mappend empty-error-result (remove success? error-results)))

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
      (let [selector (first selectors)
            v-with-validation (update-vals selection x selector #(-validate validator %))
            r (select-vals selection v-with-validation selector)]
        (if (every? success? r)                               ;; need some sort of fmap affair here
          (success-value (update-vals selection v-with-validation selector result-value))
          (prefix-errors (combine-errors r) id)))
      (let [vals (map (comp first #(select-vals selection x %)) selectors)
            r (-validate validator vals)]
        (if (success? r)
          (success-value (reduce (fn [vv [s x]] (update-vals selection vv s (constantly x))) x (map vector selectors (result-value r))))
          (prefix-errors r id)))))
  (possible-errors [this]
    (-> (possible-errors validator)
        (prefix-errors id))))

(defn run-chain [value validators]
  (if (empty? validators)
    (success-value value)
    (let [[v1 & vr] validators]
      (let [r (-validate v1 value)]
        (if (success? r)
          (run-chain (result-value r) vr)
          r)))))

(deftype ChainValidator [validators]
  Validator
  (-validate [this x]
    (let [r (run-chain x validators)]
      (if (short-circuit? r) (success-value (result-value r)) r)))
  (possible-errors [this]
    (combine-errors (map possible-errors validators))))

(deftype GroupValidator [validators]
  Validator
  (-validate [this x]
    (loop [s (success-value x)
           errors empty-error-result
           validators validators]
      (if (empty? validators)
        (if (empty? (:value errors))
          s
          errors)
        (let [r (-validate (first validators) (result-value s))]
          (if (success? r)
            (recur r errors (rest validators))
            (recur s (mappend errors r) (rest validators)))))))
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

(defn from-map
  ([m]
   (cond
     (validator? m) m
     :else (apply group (map (fn [[k v]] (at (from-map v) k [k])) m)))))

