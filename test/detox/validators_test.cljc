(ns detox.validators-test
  (:require
    [detox.core :refer [validate] :as c]
    [detox.validators :as v]
    [detox.test-utils :as u]
    #?(:clj [clojure.test :refer [deftest testing is]]))
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

(defn is= [a b]
  (is (= a b)))

(deftest greater-than-test
  (is=
    (validate 3 (v/greater-than 5))
    (u/single-error-result [:greater-than] 3 {:limit 5}))
  (is=
    (validate 5 (v/greater-than 5))
    (u/single-error-result [:greater-than] 5 {:limit 5}))
  (is=
    (validate 6 (v/greater-than 5))
    (u/success-result 6)))

(deftest less-than-test
  (is=
    (validate 3 (v/less-than -4))
    (u/single-error-result [:less-than] 3 {:limit -4}))
  (is=
    (validate -4 (v/less-than -4))
    (u/single-error-result [:less-than] -4 {:limit -4}))
  (is=
    (validate -5 (v/less-than -4))
    (u/success-result -5)))

(deftest is-integer-test
  (is=
    (validate 3 v/is-integer)
    (u/success-result 3))
  (is=
    (validate "5" v/is-integer)
    (u/success-result 5))
  (is=
    (validate "dave" v/is-integer)
    (u/single-error-result [:is-integer] "dave"))
  (is=
    (validate 3.3 v/is-integer)
    (u/single-error-result [:is-integer] 3.3)))

(deftest length-greater-than-test
  (is=
    (validate "123" (v/length-greater-than 3))
    (u/single-error-result [:length-greater-than] "123" {:limit 3}))
  (is=
    (validate "1234" (v/length-greater-than 3))
    (u/success-result "1234")))

(deftest length-less-than-test
  (is=
    (validate "123" (v/length-less-than 3))
    (u/single-error-result [:length-less-than] "123" {:limit 3}))
  (is=
    (validate "12" (v/length-less-than 3))
    (u/success-result "12")))

(deftest a>b-test
  (is=
    (validate [1 2] v/a>b)
    (u/single-error-result [:a>b] [1 2]))
  (is=
    (validate [2 2] v/a>b)
    (u/single-error-result [:a>b] [2 2]))
  (is=
    (validate [3 2] v/a>b)
    (u/success-result [3 2])))
