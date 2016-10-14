(ns detox.traversy-test
  (:require [detox.core :as c]
            [detox.validators :as v]
            [detox.traversy :as t]
            [detox.test-utils :as u]
            [traversy.lens :as l]
            #?(:clj [clojure.test :refer [deftest testing is]]))
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

(defn is= [a b]
  (is (= a b)))

(deftest traversy-selector-test
       (let [validator (-> v/mandatory (t/at :a (l/in [:a])))]
         (is=
           (c/validate {:a nil} validator)
           (u/single-error-result [:a :mandatory] nil))
         (is=
           (c/validate {:a 1} validator)
           (u/success-result {:a 1}))))

(deftest lens-law-violation-test
  (testing "that updates still work if the update causes a lens to no longer be applicable"
    (let [to-str (c/base-validator :inc-one (fn [v] (c/success-value (str v))) {})
          validator (-> to-str (t/at :odd (l/only odd?)))]
      (is=
        (c/validate [1 2 3 4] validator)
        (u/success-result ["1" 2 "3" 4])))))
