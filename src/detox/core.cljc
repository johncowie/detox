(ns detox.core
  (:require [detox.util :refer [throw-error]]))

(defn result [type value]
  {:result type :value value})

(def success-value (partial result :success))
(def short-circuit-value (partial result :break))
(def error-value (partial result :error))

(def result-value :value)

(def short-circuit? (comp (partial = :break) :result))
(def error? (comp (partial = :error) :result))
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

;; FIXME contract for selection needs to be clearer
;;  only apply validator if path or route to value is present
;;  i.e. difference between value existing and being nil, and not existing
(defprotocol Selection
  (select-vals [this x selector])
  (update-vals [this x selector f]))

(defn path-exists? [m p]
  (if (empty? p)
    true
    (and (contains? m (first p))
         (path-exists? (get m (first p)) (rest p)))))

(deftype PathSelection []
  Selection
  (select-vals [this x selector]
    (if (path-exists? x selector)
      [(get-in x selector)]
      []))
  (update-vals [this x selector f]
    (if (path-exists? x selector)
      (update-in x selector f)
      x)))

(defn new-path-selection [] (PathSelection.))

(defprotocol Validator
  (-validate [this x])
  (possible-errors [this]))

(defn apply-single-selector [x validator id selector selection optional?]
  (let [error-state (atom [])
        update-fn (fn [v] (let [r (-validate validator v)]
                            (swap! error-state conj r)
                            (result-value r)))
        updated (update-vals selection x selector update-fn)]
    (cond (and optional? (empty? @error-state)) (success-value x) ;; should this be short-circuit?  ;; probably not because there's not an issue about whether subsequent validations will be applied
          (empty? @error-state) (single-error-result [id :mandatory] nil {})
          (every? success? @error-state)                    ;; need some sort of fmap affair here
          (success-value updated)
          (every? (complement error?) @error-state)
          (short-circuit-value updated)
          :else (prefix-errors (combine-errors @error-state) id))))

(defn apply-multiple-selectors [x validator id selectors selection optional?]
  (let [valcs (map #(select-vals selection x %) selectors)
        vals (map first valcs)
        all-present (= (count (remove empty? valcs)) (count selectors))
        ]                                                   ;; TODO Verify that selector only returns one value
    (cond (and optional? (not all-present))
          (success-value x)
          (not all-present)
          (single-error-result [id :mandatory] vals {})
          :else
          (let [r (-validate validator vals)]
            (if (or (success? r) (short-circuit? r))
              (success-value (reduce (fn [vv [s x]] (update-vals selection vv s (constantly x))) x (map vector selectors (result-value r))))
              (prefix-errors r id))))))

(defrecord NestedValidator [id selectors validator selection optional?]
  Validator
  (-validate [this x]
    (case (count selectors)
      0 (throw-error "Not enough selectors!!")
      1 (apply-single-selector x validator id (first selectors) selection optional?)
      (apply-multiple-selectors x validator id selectors selection optional?)))
  (possible-errors [this]
    (-> (possible-errors validator)
        (prefix-errors id))))

(defn bind [m validator]
  (if (or (error? m) (short-circuit? m))
    m
    (-validate validator (result-value m))))

(deftype ChainValidator [validators]
  Validator
  (-validate [this x]
    (reduce bind (success-value x) validators))
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
    (if #_(nil? x) false
      (single-error-result [:mandatory] x {})
      (let [result (validate-f x)]
        (if (error? result)
          (single-error-result [id] x constraints)
          result))))
  (possible-errors [this]
    (if no-error
      empty-error-result
      (single-error-result [id] "...value..." constraints))))

(defn validate [x validator]
  (let [r (-validate validator x)]
    ;(if (short-circuit? r) (success-value (result-value r)) r)
    r
    ))

(defn chain [& validators]
  (ChainValidator. validators))

(defn group [& validators]
  (GroupValidator. validators))

(defn with-selection [validator selection]
  (assoc validator :selection selection))

(defn at
  [validator id & selectors]
  (NestedValidator. id selectors validator (PathSelection.) false))

(defn optional-at
  [validator id & selectors]
  (assoc (apply at validator id selectors) :optional? true))

(defn base-validator
  ([id validate-f constraints no-error]
   (if (and id validate-f)
     (ValueValidator. id validate-f constraints no-error)
     (throw-error "Not a valid unit validator")))
  ([id validate-f constraints]
   (base-validator id validate-f constraints false)))

(defn validator? [x]
  (satisfies? Validator x))

;; TODO needs a function of type ([m a] => m [a]) - look up what this is called in haskell

(deftype CollectionValidator [validator]
  Validator
  (-validate [this x]                                       ;; TODO check that x is a collection
    (let [rs (map (partial -validate validator) x)
          vs (map result-value rs)]
      (cond
        (every? success? rs)
        (success-value vs)
        (every? (complement error?) rs)
        (short-circuit-value vs)
        :else (error-value (apply concat (map result-value (remove success? rs)))))))
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

(deftype OptionalValidator [validator]
  Validator
  (-validate [this x]
    (if (nil? x)
      (success-value x)
      (-validate validator x)))
  (possible-errors [this]
    (possible-errors validator)))

;; Value cases
;;   No paths to value
;;   Paths to value but is nil
;;   Path has value

(defn optionally [validator]
  (OptionalValidator. validator))

