;;; The intention of Clens is to be a simple but powerful validation library
;;; that is built around the idea of model lenses.

;;; It has the following requirements:
;;; - should be easy to apply validations to any given field that is pointed to by a lens
;;; - if a lens returns multiple foci, then the validations can be supplied to each focus
;;; - the framework should allow dependencies to be expressed
;;;          (i.e. only do this validation if these other things are valid)
;;; - there should be an easy and consistent way to support error messages, or return
;;;    the error information in a way that the messages are easy to generate
;;;       (consider interpolation)


;; Requirements
;; can specify order of validations for a particular field
;; can specify order of validations between fields
;; can write functions that generate error messages
;; error messages should be able to support interpolation of erroring values
;; user should be able to choose identifier for validation message
;;   possibly identifier could be packaged with failing values
;;   can somehow indicate dependencies between validations

;; More thoughts
;; to keep validation and messaging separate, validation should return as much info as possible
;;  i.e. the validation id, the failure id, any relevant constraints, and the failing value
;;  perhaps supply mechanism for generating message at validation point

(ns clens.core
  (:require [traversy.lens :as l]))

;; currently just thoughts on what a validation framework should look like...
;; do we need validate-single and validate-all? - would correspond to view-single and view
;;   let's mainly worry about the view case

(defn return-model [id r]
  (when-not (empty? r)
    [id r]))

(defn chain [validate-fns]
  (fn [v]
    (when-let [r ((apply some-fn validate-fns) v)]
      (when (not= :success r) r))))

(defn validate-one [model validation]
  (let [[id lens validate-fns] validation]
    (->> (l/view model lens)
         (map (chain validate-fns))
         (remove nil?)
         (return-model id))))

(defn validate [model validations]
  (reduce #(merge %1 (validate-one model %2)) nil validations))


;;;; validations

(defn mandatory [v]
  (when (nil? v) {:error :mandatory}))

(defn optional [v]
  (when (nil? v) :success))

(defn error-map
  ([err-key v] {:error err-key :value v})
  ([err-key v c]
     (if c
       (assoc (error-map err-key v) :constraint c)
       (error-map err-key v))))

(defn- when-pred [pr err-key c]
  (fn [v]
    (when (pr v)
      (error-map err-key v c))))

(defn- when-not-pred [pr err-key c]
  (when-pred (complement pr) err-key c))

(defn- length [comparator err-key l]
  (when-not-pred #(comparator (count %) l) err-key l))

(defn- compare-number [comparator err-key n]
  (when-not-pred #(comparator % n) err-key n))

(def greater-than (partial compare-number > :greater-than))
(def greater-than-or-equal-to (partial compare-number >= :greater-than-or-equal-to))
(def less-than (partial compare-number < :less-than))
(def less-than-or-equal-to nil)

(def min-length (partial length >= :min-length))
(def max-length (partial length <= :max-length))
(def exact-length (partial length = :exact-length))

(def is-string (when-not-pred string? :string nil))
(def is-number (when-not-pred number? :number nil))
