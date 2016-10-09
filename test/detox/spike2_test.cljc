(ns detox.spike2-test
  (:require
    [detox.core :as c]
    [detox.validators :as v]
    [traversy.lens :as l]
    [detox.test-utils :as u]
    [detox.traversy :as t]
    #?(:clj
    [clojure.test :refer [deftest testing is]])
    #?(:clj
    [detox.macros :refer [defvalidator defpredicate]]))
  #?(:cljs (:require-macros
             [cljs.test :refer [deftest testing is]]
             [detox.macros :refer [defvalidator defpredicate]])))

(defn is= [a b] (is (= a b)))

(deftest validators-test
  (testing "validation that value exists"
    (is=
      (c/validate 2 v/mandatory)
      (u/success-result 2))
    (is=
      (c/validate 3 v/mandatory)
      (u/success-result 3))
    (is=
      (c/validate nil v/mandatory)
      (u/single-error-result [:mandatory] nil)))
  (testing "validation that value is greater than another value"
    (is=
      (c/validate 2 (v/greater-than 1))
      (u/success-result 2))
    (is=
      (c/validate 2 (v/greater-than 2))
      (u/single-error-result [:greater-than] 2 {:limit 2}))
    (is=
      (c/validate 4 (v/greater-than 5))
      (u/single-error-result [:greater-than] 4 {:limit 5})))
  (testing "updating the selector on a unit validator"
    (let [age-mandatory (c/at v/mandatory :age-field [:age])]
      (is=
        (c/validate {:age 4} age-mandatory)
        (u/success-result {:age 4}))
      (is=
        (c/validate {:age nil} age-mandatory)
        (u/single-error-result [:age-field :mandatory] nil))
      (is=
        (c/validate {} age-mandatory)
        (u/single-error-result [:age-field :mandatory] nil))))
  (testing "chaining validators together - validator in first arg run first"
    (let [mandatory-and-gt-3 (c/chain v/mandatory (v/greater-than 3))]
      (is=
        (c/validate nil mandatory-and-gt-3)
        (u/single-error-result [:mandatory] nil))
      (is=
        (c/validate 1 mandatory-and-gt-3)
        (u/single-error-result [:greater-than] 1 {:limit 3}))
      (is=
        (c/validate 3 mandatory-and-gt-3)
        (u/single-error-result [:greater-than] 3 {:limit 3}))
      (is=
        (c/validate 4 mandatory-and-gt-3)
        (u/success-result 4)))
    (testing "chainging two validators together with a new selector"
      (let [age-mandatory-and-gt-3 (c/at (c/chain v/mandatory (v/greater-than 3)) :age-field [:age])]
        (is=
          (c/validate nil age-mandatory-and-gt-3)
          (u/single-error-result [:age-field :mandatory] nil))
        (is=
          (c/validate {:age 3} age-mandatory-and-gt-3)
          (u/single-error-result [:age-field :greater-than] 3 {:limit 3}))
        (is=
          (c/validate {:age 4} age-mandatory-and-gt-3)
          (u/success-result {:age 4}))))
    (testing "chaining two chained validators together"
      (let [chain1 (c/at (c/chain v/mandatory (v/greater-than 5)) :1 [:no1])
            chain2 (c/at (c/chain v/mandatory (v/greater-than 5)) :2 [:no2])
            chain (c/chain chain1 chain2)]
        (is=
          (c/validate {:no1 nil :no2 nil} chain)
          (u/single-error-result [:1 :mandatory] nil))
        (is=
          (c/validate {:no1 3 :no2 nil} chain)
          (u/single-error-result [:1 :greater-than] 3 {:limit 5}))
        (is=
          (c/validate {:no1 6 :no2 nil} chain)
          (u/single-error-result [:2 :mandatory] nil))
        (is=
          (c/validate {:no1 6 :no2 3} chain)
          (u/single-error-result [:2 :greater-than] 3 {:limit 5}))
        (is=
          (c/validate {:no1 6 :no2 7} chain)
          (u/success-result {:no1 6 :no2 7})))))
  (testing "joining multiple selectors together"
    (let [v (-> v/mandatory (c/at :age-f [:age]) (c/at :person-f [:person]))]
      (is=
        (c/validate {:person {:age 1}} v)
        (u/success-result {:person {:age 1}}))
      (is=
        (c/validate {:person {:age nil}} v)
        (u/single-error-result [:person-f :age-f :mandatory] nil))))
  (testing "chaining multiple validators together"
    (let [chained (c/chain v/mandatory (v/greater-than 3) (v/less-than 10))]
      (is=
        (c/validate nil chained)
        (u/single-error-result [:mandatory] nil))
      (is=
        (c/validate 2 chained)
        (u/single-error-result [:greater-than] 2 {:limit 3}))
      (is=
        (c/validate 30 chained)
        (u/single-error-result [:less-than] 30 {:limit 10}))
      (is=
        (c/validate 5 chained)
        (u/success-result 5))))
  (testing "combining two unit validators together in parallel"
    (let [par (c/group (v/greater-than 3) (v/greater-than 10))]
      (is=
        (c/validate 1 par)
        (u/error-result (u/single-error [:greater-than] 1 {:limit 3})
                        (u/single-error [:greater-than] 1 {:limit 10})))
      (is=
        (c/validate 4 par)
        (u/single-error-result [:greater-than] 4 {:limit 10}))
      (is=
        (c/validate 11 par)
        (u/success-result 11))))
  (testing "exploring possible errors"
    (testing "interogating unit validator for possible errors"
      (is=
        (c/possible-errors v/mandatory)
        (u/single-error-result [:mandatory] "...value..."))
      (is=
        (c/possible-errors (v/greater-than 7.9))
        (u/single-error-result [:greater-than] "...value..." {:limit 7.9}))
      (testing "optional is a special form that can never return an error"
        (is=
          (c/possible-errors v/optional)
          (u/error-result))))
    (testing "interogating nested validator for possible errors"
      (is=
        (c/possible-errors (c/at v/mandatory :age [:age]))
        (u/single-error-result [:age :mandatory] "...value..."))
      (is=
        (c/possible-errors (-> (v/greater-than -3) (c/at :a [:a]) (c/at :b [:b])))
        (u/single-error-result [:b :a :greater-than] "...value..." {:limit -3})))
    (testing "possible errors for chain"
      (is=
        (c/possible-errors (c/chain v/mandatory (v/greater-than 1)))
        (u/error-result (u/single-error [:mandatory] "...value...")
                        (u/single-error [:greater-than] "...value..." {:limit 1}))))
    (testing "interogating composite validator for possible errors"
      (is=
        (c/possible-errors (c/group
                             (c/at (c/chain v/mandatory (v/greater-than 4)) :age [:age])
                             (c/at (c/chain v/mandatory (v/less-than 2)) :name [:name])))
        (u/error-result (u/single-error [:age :mandatory] "...value...")
                        (u/single-error [:age :greater-than] "...value..." {:limit 4})
                        (u/single-error [:name :mandatory] "...value...")
                        (u/single-error [:name :less-than] "...value..." {:limit 2})))
      (testing "if optional is involved it is ignored"
        (is=
          (c/possible-errors (c/chain v/optional (v/greater-than 3)))
          (u/single-error-result [:greater-than] "...value..." {:limit 3})))))
  (testing "adding parsers to the mix"
    (is=
      (c/validate nil v/is-integer)
      (u/single-error-result [:is-integer] nil))
    (is=
      (c/validate 1 v/is-integer)
      (u/success-result 1))
    (is=
      (c/validate 1.1 v/is-integer)
      (u/single-error-result [:is-integer] 1.1))
    (is=
      (c/validate "2" v/is-integer)
      (u/success-result 2))
    (is=
      (c/validate "2.3" v/is-integer)
      (u/single-error-result [:is-integer] "2.3"))
    (testing "parsed values are passed through in chain"
      (is=
        (c/validate "3" (c/chain v/is-integer (v/greater-than 3)))
        (u/single-error-result [:greater-than] 3 {:limit 3})) ;; TODO should this be the initial value?
      (is= (c/validate "4" (c/chain v/is-integer (v/greater-than 3)))
           (u/success-result 4)))
    (testing "parsed values are passed up nested validation"
      (is=
        (c/validate {:age "3"} (c/at v/is-integer :age [:age]))
        (u/success-result {:age 3})))
    (testing "parsed values are passed up with parallel structure"
      (let [validator (c/group (c/at (c/chain v/is-integer (v/greater-than 4)) :age [:age])
                               (c/at (c/chain v/is-integer (v/greater-than 3)) :favNumber [:favNumber]))]
        (is=
          (c/validate {:age "10" :favNumber "6"} validator)
          (u/success-result {:age 10 :favNumber 6})))))
  (testing "can break out of chain if optional value is specified"
    (let [optional-integer (c/chain v/optional v/is-integer)]
      (is=
        (c/validate nil optional-integer)
        (u/success-result nil))
      (is=
        (c/validate 1.1 optional-integer)
        (u/single-error-result [:is-integer] 1.1))
      (is=
        (c/validate "3" optional-integer)
        (u/success-result 3))))
  (testing "supports selectors that point to multiple foci - e.g. with traversy"
    (let [->each-val (l/*> (l/in [:vals]) l/each)
          validator (t/at (c/chain v/is-integer (v/less-than 5)) :vals ->each-val)]
      (is=
        (c/validate {:vals [1 "2" 3 "4"]} validator)
        (u/success-result {:vals [1 2 3 4]}))
      (is=
        (c/validate {:vals [1 2 3 "4" 5 6]} validator)
        (u/error-result (u/single-error [:vals :less-than] 5 {:limit 5})
                        (u/single-error [:vals :less-than] 6 {:limit 5})))))
  #_(future-facts "can declare dependencies on other parallel validations"
                  (future-fact "can check for non-existant dependencies"))
  (testing "can run validations that compare multiple values"
    ;; some way of checking arity of function, in conjunction with what is being selected?
    (let [validator (c/at v/a>b :ages [:maxAge] [:minAge])]
      (is=
        (c/validate {:minAge 10 :maxAge 12} validator)
        (u/success-result {:minAge 10 :maxAge 12}))
      (is=
        (c/validate {:minAge 11 :maxAge 10} validator)
        (u/single-error-result [:ages :a>b] [10 11])))
    (testing "can also update values with at*"
      (let [add-one (c/base-validator [:+1] (fn [v] (c/success-value (map (partial + 1) v))) {})
            validator (c/at add-one :numbers [:a] [:b] [:c])]
        (is=
          (c/validate {:a 2 :b 4 :c 6} validator)
          (u/success-result {:a 3 :b 5 :c 7})))))
  #_(future-facts "can use 'or' operator to combine validators"
                  ;; DOES THIS MAKE SENSE?  if all clauses fail, should all errors be displayed, or just the last one?
                  ;;   currently thinking is that this should be done at the unit validator level
                  ;;   could throw an exception if 'or' is not called on unit validators
                  )
  #_(future-facts "protocol can be subbed in to deal with how identifiers are joined together")
  )

(defpredicate matches-string [v string]
              (= v string))

(defvalidator capital-john [v]
              (if (= v "john")
                (c/success-value "JOHN")
                (c/error-value v)))

(testing "NICE_TO_HAVE can use macro to generate validators"
  (is=
    (c/validate "bear" (matches-string "bear"))
    (u/success-result "bear"))
  (is=
    (c/validate "bob" (matches-string "bear"))
    (u/single-error-result [:matches-string] "bob" {:string "bear"}))
  (is=
    (c/validate "john" capital-john)
    (u/success-result "JOHN"))
  (is=
    (c/validate "jo" capital-john)
    (u/single-error-result [:capital-john] "jo")))

;; QUESTIONS
;;   - for errors, should the original values be preserved (i.e. without parsing)
;;   - for grouping validations, should identifiers be mandated?
;;   - should it default to v/mandatory, with an optional wrapper for removing this property?
;;     - FIXME optional should be a special form because it can't return an error

;; EMERGING PROBLEMS
;;   X updates for selectors that point to multiple things
;;       step1 - apply as update to selector
;;       step2 - retrieve validation results using original selector
;;       step3 - apply update function to selector that strips out values from success results, if there are no errors
;;   - all validations should return list (or map) of errors, return type needs to be unified

;; ROAD MAP
;;   - create function that transforms traditional model-based validation into proper validator
;;   - start to organise code properly
;;   - target both clj and cljs
;;   - write a bunch more validators
;;   - write something (maybe schema) that validates that the validator is constructed correctly
;;   X put 'get' and 'update' functions into protocol
;;   - translation framework
;;   - start working on kickass readme
;;   - can roll at and at* into one function?
;;   X make use of macros in validators
;;   - add testing for cljs
;;   X nested validator should encapsulate which selector mechanism it uses, not the validate function
