(ns detox.validators
  (:require [detox.core :refer [base-validator success-value error-value short-circuit-value]]
            [detox.util :refer [parse-int]]
            #?(:clj [detox.macros :refer [defvalidator defpredicate]]))
  #?(:cljs (:require-macros [detox.macros :refer [defvalidator defpredicate]])))

;; mandatory
(declare mandatory optional greater-than less-than
         coerce-integer is-string length length-greater-than length-less-than a>b matches-regex)

(defpredicate mandatory [v] (not (nil? v)))

;; FIXME figure out how to either macro this or do optional another way
(def optional
  (base-validator :optional (fn [v] (if (nil? v) (short-circuit-value v) (success-value v))) {} true))

;; greater-than
(defpredicate greater-than [v limit]
              (> v limit))

(defpredicate less-than [v limit]
              (< v limit))

;; TODO generalise parsing so it's easier to make more of them
(defvalidator coerce-integer [v]
              (if (integer? v)
                (success-value v)
                (try (success-value (parse-int v))
                     (catch #?(:clj Exception :cljs js/Error) e (error-value v)))))

(defpredicate is-string [v] (string? v))

;; TODO move to model
(defn update-result-value [r v]
  (assoc r :value v))

(defn length [{:keys [validate-f id constraints]}]
  (base-validator (keyword (str "length-" (name id)))
                  (fn [v] (-> (count v) validate-f (update-result-value v)))
                  constraints))

(def length-greater-than (comp length greater-than))
(def length-less-than (comp length less-than))

(defpredicate a>b [[a b]] (> a b))

(defpredicate matches-regex [v regex] (re-matches (re-pattern regex) v))