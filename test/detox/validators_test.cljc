(ns detox.validators-test
  (:require
    [detox.core :refer [validate]]
    [detox.validators :as v]
    [detox.test-utils :as u]
    #?(:clj
    [clojure.test :refer [deftest testing is are]]))
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is are]])))

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

(deftest coerce-integer-test
  (is=
    (validate 3 v/coerce-integer)
    (u/success-result 3))
  (is=
    (validate "5" v/coerce-integer)
    (u/success-result 5))
  (is=
    (validate "dave" v/coerce-integer)
    (u/single-error-result [:coerce-integer] "dave"))
  (is=
    (validate 3.3 v/coerce-integer)
    (u/single-error-result [:coerce-integer] 3.3)))

(deftest is-string-test
  (are [input output]
    (= (validate input v/is-string) output)
    "abc" (u/success-result "abc")
    "" (u/success-result "")
    23 (u/single-error-result [:is-string] 23)
    :bob (u/single-error-result [:is-string] :bob)))

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

(deftest match-regex-test
  (are [input regex output]
    (= (validate input (v/matches-regex regex)) output)
    "a" "a" (u/success-result "a")
    "b" "a" (u/single-error-result [:matches-regex] "b" {:regex "a"})
    "123" "[0-9]*" (u/success-result "123")
    "12a3" "[0-9]*" (u/single-error-result [:matches-regex] "12a3" {:regex "[0-9]*"})
    ))