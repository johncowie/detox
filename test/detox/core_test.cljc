(ns detox.core-test
  (:require [detox.core :as c]
            [detox.validators :as v]
            [detox.test-utils :as u]
            [detox.util :as ut]
            #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])))

(defn is= [a b]
  (is (= a b)))

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
  (testing "can validate value in map"
    (let [validator (c/at v/is-integer :age [:age])]
      (is= (c/validate {:age 1} validator)
           (u/success-result {:age 1}))
      (is=
        (c/validate {:age "bob"} validator)
        (u/single-error-result [:age :is-integer] "bob")))))

(deftest group-test
  (testing "can group validations in parallel"
    (let [validator (c/group v/is-string v/is-integer)]
      (is= (c/validate "1" validator) (u/success-result 1))
      (is= (c/validate "str" validator) (u/single-error-result [:is-integer] "str"))
      (is= (c/validate 1 validator) (u/single-error-result [:is-string] 1)))))

(deftest each-is-test
  (testing "can apply a validator to a collection"
    (let [validator (c/each-is v/is-integer)]
      (is= (c/validate [] validator) (u/success-result []))
      (is= (c/validate [1] validator) (u/success-result [1]))
      (is= (c/validate ["1" "2"] validator) (u/success-result [1 2]))
      (is= (c/validate ["1" "a" "2" "b"] validator) (u/error-result (u/single-error [:is-integer] "a")
                                                                    (u/single-error [:is-integer] "b"))))))

(deftest from-map-test
  ;; FIXME what to do if selector is called on part of data structure that is not a map
  ;;  catch selector exception and rethrow
  (testing "vanilla map validator"
    (let [validator (c/from-map {:name v/is-string
                                 :xs   {:a v/is-integer :b v/is-integer}})]
      (is=
        (c/validate {:name "bob" :xs {:a 1 :b 2}} validator)
        (u/success-result {:name "bob" :xs {:a 1 :b 2}}))
      (is=
        (c/validate {:name "bob" :xs {:a "a" :b "b"}} validator)
        (u/error-result (u/single-error [:xs :a :is-integer] "a")
                        (u/single-error [:xs :b :is-integer] "b")))
      (is=
        (c/validate {:name 1 :xs {:a 1 :b "b"}} validator)
        (u/error-result (u/single-error [:name :is-string] 1)
                        (u/single-error [:xs :b :is-integer] "b")))))
  (testing "validating a collection"
    (let [validator (c/from-map {:col [v/is-integer]})]
      (is=
        (c/validate {:col []} validator)
        (u/success-result {:col []}))
      (is=
        (c/validate {:col ["1"]} validator)
        (u/success-result {:col [1]}))
      (is=
        (c/validate {:col ["1" "blah" "blob"]} validator)
        (u/error-result (u/single-error [:col :is-integer] "blah")
                        (u/single-error [:col :is-integer] "blob")))))
  )
