(ns detox.core-test
  (:require [detox.core :as c]
            [detox.validators :as v]
            [detox.test-utils :as u :refer [future-testing]]
            [detox.util :as ut]
            [detox.traversy :as t]
            [traversy.lens :as l]
    #?(:clj
            [clojure.test :refer [deftest testing is are]]
       :cljs [cljs.test :refer-macros [deftest testing is are]])))

(defn is= [a b]
  (is (= a b)))


(deftest path-selection-test
  (let [selection (c/new-path-selection)]
    (testing "that get works correctly: "
      (testing "path not present"
        (is= (c/select-vals selection {} [:a :b]) []))
      (testing "path present but value is nil"
        (is= (c/select-vals selection {:a {:b nil}} [:a :b]) [nil]))
      (testing "path present value is not nil"
        (is= (c/select-vals selection {:a {:b 1}} [:a :b]) [1])))
    (testing "that update works correctly: "
      (testing "path not present"
        (is= (c/update-vals selection {} [:a :b] str) {}))
      (testing "path present but value is nil"
        (is= (c/update-vals selection {:a {:b nil}} [:a :b] str) {:a {:b ""}}))
      (testing "path present value is not nil"
        (is= (c/update-vals selection {:a {:b 1}} [:a :b] str) {:a {:b "1"}})))))

(deftest optional-value-test
  (testing "mandatory doesn't comes out of the box"
    (let [validator (c/chain v/mandatory v/coerce-integer)]
      (is= (c/validate "1" validator)
           (u/success-result 1))
      (is= (c/validate nil validator)
           (u/single-error-result [:mandatory] nil))
      (is= (c/validate "bob" validator)
           (u/single-error-result [:coerce-integer] "bob"))))
  #_(testing "can wrap with optional"                       ;; TODO future
      (let [validator (c/optionally v/coerce-integer)]
        (is= (c/validate "1" validator)
             (u/success-result 1))
        (is= (c/validate nil validator)
             (u/success-result nil))
        (is= (c/validate "bob" validator)
             (u/single-error-result [:coerce-integer] "bob")))
      (testing "when optional is applied to nested value"   ;; TODO future
        (let [validator (-> (c/optionally v/coerce-integer) (c/at :age [:age]))]
          (is= (c/validate {} validator)
               (u/success-result {}))
          (is= (c/validate {:age 1} validator)
               (u/success-result {:age 1}))
          (is= (c/validate {:age "bob"} validator)
               (u/single-error-result [:coerce-integer] "bob"))))))

(deftest optional-at-test
  (testing "mandatory doesn't yet come out of the box"      ;; FIXME
    (let [validator (c/at (c/chain v/mandatory v/coerce-integer) :a [:a])]
      (is= (c/validate {:a "1"} validator)
           (u/success-result {:a 1}))
      (is= (c/validate {:a nil} validator)
           (u/single-error-result [:a :mandatory] nil))
      (is= (c/validate {} validator)
           (u/single-error-result [:a :mandatory] nil))
      ))

  ;; TODO demo with multiple nesting
  (testing "can specify optional at selector"
    (let [validator (c/optional-at (c/chain v/mandatory v/coerce-integer) :a [:a])]
      (is= (c/validate {:a nil} validator)
           (u/single-error-result [:a :mandatory] nil))
      (is= (c/validate {} validator)
           (u/success-result {})))))

(deftest error-results-test
  (testing "base error result"
    (is= c/empty-error-result
         {:result :error :value []})
    (is= (c/single-error-result [:greater-than] 4 {:limit 3})
         (u/single-error-result [:greater-than] 4 {:limit 3})))
  (testing "can combine errors"
    (is=
      (c/combine-errors [(c/single-error-result [:age :mandatory] nil {})
                         (c/single-error-result [:age :mandatory] nil {})
                         (c/single-error-result [:age :less-than] 3 {:limit 4})])
      (u/error-result (u/single-error [:age :mandatory] nil)
                      (u/single-error [:age :mandatory] nil)
                      (u/single-error [:age :less-than] 3 {:limit 4}))))
  (testing "can prefix errors"
    (is=
      (c/prefix-errors (c/single-error-result [:greater-than] 8 {:limit 5}) :blah)
      (u/single-error-result [:blah :greater-than] 8 {:limit 5}))))

(deftest at-test
  (testing "throws error if no selectors are provided"
    (is (thrown? #?(:clj Exception :cljs js/Error) (c/validate {:age 1} (c/at v/coerce-integer :age)))))
  (testing "can validate value in map"
    (let [validator (c/at v/coerce-integer :age [:age])]
      (are [input output]
        (= (c/validate input validator) output)
        {} (u/single-error-result [:age :mandatory] nil)
        {:age 1} (u/success-result {:age 1})
        {:age "bob"} (u/single-error-result [:age :coerce-integer] "bob"))))
  (testing "case where a short-circuit validator is used"
    (let [validator (c/at v/optional :age [:age])]
      (are [i o]
        (= (c/validate i validator) o)
        {} (u/single-error-result [:age :mandatory] nil)    ;; path is still mandatory
        {:age nil} (u/short-circuit-result {:age nil})
        {:age 1} (u/success-result {:age 1}))))
  (testing "can validate value in map only if path exists"
    (let [validator (c/optional-at v/coerce-integer :age [:age])]
      (are [input output]
        (= (c/validate input validator) output)
        {} (u/success-result {})
        {:age 1} (u/success-result {:age 1})
        {:age "gary"} (u/single-error-result [:age :coerce-integer] "gary"))))
  (testing "can validate values at multiple selectors"
    (let [validator (c/at (c/each-is v/coerce-integer) :abc [:a] [:b] [:c])]
      (are [input output]
        (= (c/validate input validator) output)
        {} (u/single-error-result [:abc :mandatory] [nil nil nil])
        {:a 1 :b 2} (u/single-error-result [:abc :mandatory] [1 2 nil])
        {:a 1 :b 2 :c "bob"} (u/single-error-result [:abc :coerce-integer] "bob")
        {:a "1" :b "2" :c "3"} (u/success-result {:a 1 :b 2 :c 3}))))
  (testing "can optionally validate values at multiple selectors"
    (let [validator (c/optional-at (c/each-is v/coerce-integer) :abc [:a] [:b] [:c])]
      (are [input output]
        (= (c/validate input validator) output)
        {} (u/success-result {})
        {:a 1} (u/success-result {:a 1})
        {:a 1 :b "b"} (u/success-result {:a 1 :b "b"})
        {:a 2 :b 2 :c 3} (u/success-result {:a 2 :b 2 :c 3})
        {:a "a" :b "b" :c 3} (u/error-result (u/single-error [:abc :coerce-integer] "a")
                                             (u/single-error [:abc :coerce-integer] "b")))))
  (testing "with traversy"                                  ;; FIXME remove these traversy tests
    (testing "mandatory"
      (let [validator (t/at v/mandatory :age (l/in [:age]))]
        (are [i o]
          (= (c/validate i validator) o)
          {} (u/single-error-result [:age :mandatory] nil)
          {:age nil} (u/single-error-result [:age :mandatory] nil))))
    (testing "optional"
      (let [validator (t/at v/optional :age (l/in [:age]))]
        (are [i o]
          (= (c/validate i validator) o)
          {} (u/single-error-result [:age :mandatory] nil)  ;; path is still mandatory
          {:age nil} (u/short-circuit-result {:age nil})
          {:age 1} (u/success-result {:age 1}))))))

(deftest chain-test
  (testing "chaining validations together so that it short-circuits on first error encountered"
    (testing "empty chain"
      (is= (c/validate 1 (c/chain))
           (u/success-result 1)))
    (testing "chain of one validator"
      (is= (c/validate 1 (c/chain v/mandatory))
           (u/success-result 1))
      (is= (c/validate nil (c/chain v/mandatory))
           (u/single-error-result [:mandatory] nil)))
    (testing "chain of two validators"
      (is= (c/validate 1 (c/chain v/mandatory (v/greater-than 0)))
           (u/success-result 1))
      (is= (c/validate nil (c/chain v/mandatory (v/greater-than 0)))
           (u/single-error-result [:mandatory] nil))
      (is= (c/validate -1 (c/chain v/mandatory (v/greater-than 0)))
           (u/single-error-result [:greater-than] -1 {:limit 0})))
    (testing "short-circuiting from chain"
      (is= (c/validate 1 (c/chain v/optional (v/greater-than 0)))
           (u/success-result 1))
      (is= (c/validate nil (c/chain v/optional (v/greater-than 0)))
           (u/short-circuit-result nil))
      (is= (c/validate -1 (c/chain v/optional (v/greater-than 0)))
           (u/single-error-result [:greater-than] -1 {:limit 0})))
    ))

(deftest group-test
  (testing "can group validations in parallel"
    (testing "empty group just returns success result"
      (is= (c/validate nil (c/group))
           (u/success-result nil)))
    (testing "group of one"
      (is= (c/validate "1" (c/group v/mandatory))
           (u/success-result "1"))
      (is= (c/validate nil (c/group v/mandatory))
           (u/single-error-result [:mandatory] nil)))
    (testing "group of two"
      (let [validator (c/group v/is-string v/coerce-integer)]
        (is= (c/validate "1" validator) (u/success-result 1))
        (is= (c/validate "str" validator) (u/single-error-result [:coerce-integer] "str"))
        (is= (c/validate 1 validator) (u/single-error-result [:is-string] 1))))))

(deftest each-is-test
  (testing "can apply a validator to a collection"
    (let [validator (c/each-is v/coerce-integer)]
      (is= (c/validate [] validator) (u/success-result []))
      (is= (c/validate [1] validator) (u/success-result [1]))
      (is= (c/validate ["1" "2"] validator) (u/success-result [1 2]))
      (is= (c/validate ["1" "a" "2" "b"] validator) (u/error-result (u/single-error [:coerce-integer] "a")
                                                                    (u/single-error [:coerce-integer] "b")))))
  (testing "can apply each-is to optional, for short-circuiting purposes"
    (let [validator (c/each-is v/optional)]
      (are [i o]
        (= (c/validate i validator) o)
        [] (u/success-result [])
        [nil] (u/short-circuit-result [nil])
        [1 nil] (u/short-circuit-result [1 nil])
        [1 2] (u/success-result [1 2])))
    (testing "accumulates breaks - multi selector"
      (let [validator (c/at (c/chain (c/each-is v/optional) v/a>b) :ab [:a] [:b])]
        (are [i o]
          (= (c/validate i validator) o)
          {:a 3 :b 2} (u/success-result {:a 3 :b 2})
          {:a 2 :b 2} (u/single-error-result [:ab :a>b] [2 2])
          {:a 1 :b nil} (u/success-result {:a 1 :b nil})
          {:a nil :b nil} (u/success-result {:a nil :b nil}))))))

(deftest from-map-test
  ;; FIXME what to do if selector is called on part of data structure that is not a map
  ;;  catch selector exception and rethrow
  (testing "vanilla map validator"
    (let [validator (c/from-map {:name v/is-string
                                 :xs   {:a v/coerce-integer :b v/coerce-integer}})]
      (is=
        (c/validate {:name "bob" :xs {:a 1 :b 2}} validator)
        (u/success-result {:name "bob" :xs {:a 1 :b 2}}))
      (is=
        (c/validate {:name "bob" :xs {:a "a" :b "b"}} validator)
        (u/error-result (u/single-error [:xs :a :coerce-integer] "a")
                        (u/single-error [:xs :b :coerce-integer] "b")))
      (is=
        (c/validate {:name 1 :xs {:a 1 :b "b"}} validator)
        (u/error-result (u/single-error [:name :is-string] 1)
                        (u/single-error [:xs :b :coerce-integer] "b")))))
  (testing "validating a collection"
    (let [validator (c/from-map {:col [v/coerce-integer]})]
      (is=
        (c/validate {:col []} validator)
        (u/success-result {:col []}))
      (is=
        (c/validate {:col ["1"]} validator)
        (u/success-result {:col [1]}))
      (is=
        (c/validate {:col ["1" "blah" "blob"]} validator)
        (u/error-result (u/single-error [:col :coerce-integer] "blah")
                        (u/single-error [:col :coerce-integer] "blob")))))
  (testing "validating a collection of maps"
    (let [validator (c/from-map {:col [{:a v/coerce-integer
                                        :b (v/greater-than 3)}]})]
      (is=
        (c/validate {:col []} validator)
        (u/success-result {:col []}))
      (is=
        (c/validate {:col [{:a "1" :b 4}]} validator)
        (u/success-result {:col [{:a 1 :b 4}]}))
      (is=
        (c/validate {:col [{:a 1 :b 2} {:a 2 :b 5} {:a "bob" :b 7}]} validator)
        (u/error-result
          (u/single-error [:col :b :greater-than] 2 {:limit 3})
          (u/single-error [:col :a :coerce-integer] "bob"))))))
