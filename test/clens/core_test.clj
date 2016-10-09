(ns clens.core-test
  (:require [clens.core :as c]
            [traversy.lens :as l]))

(def model
  {:id 1
   :firstname "Immanuel"
   :lastname "Kant"
   :friends ["Hume" "Bentham"]})

(defn max-length [n]
  (fn [v] (when (> (count v) n) [:max-length n v])))

(def always-passes (constantly nil))
(defn always-fails [v] [:always-fails v])

;(facts "that validations on individual fields work"
;  (let [validations [[:foo (l/in [:firstname]) [always-passes]]]]
;    (c/validate model validations) => nil)
;
;  (let [validations [[:foo (l/in [:firstname]) [always-fails]]]]
;    (c/validate model validations) => {:foo [[:always-fails "Immanuel"]]})
;
;  (let [validations [[:foo (l/in [:firstname]) [always-fails]]
;                     [:bar (l/in [:lastname]) [always-fails]]]]
;    (c/validate model validations) => {:foo [[:always-fails "Immanuel"]]
;                                       :bar [[:always-fails "Kant"]]}))
;
;(facts "that validations on lenses with multiple foci work"
;  (let [validations [[:foo (l/*> (l/in [:friends]) l/each) [always-fails]]]]
;    (c/validate model validations) => {:foo [[:always-fails "Hume"]
;                                             [:always-fails "Bentham"]]}))
;
;(facts "that validations on lenses with no foci work"
;  (let [validations [[:foo (l/*> (l/in [:unknown]) l/each) always-fails]]]
;    (c/validate model validations) => nil))
;
;(facts "can run multiple validations on a lens"
;  (let [validations [[:foo (l/in [:firstname]) [(fn [v] :fail1) (fn [v] :fail2)]]]]
;    (c/validate model validations) => {:foo [:fail1]})
;  (let [validations [[:foo (l/in [:firstnmae]) [(fn [v] nil) (fn [v] :fail2)]]]]
;    (c/validate model validations) => {:foo [:fail2]}))
;
;(facts "can shortcut from validation list for a lens with success token"
;  (let [validations [[:foo (l/in [:firstname]) [(constantly nil)
;                                                (constantly :success)
;                                                (constantly :fail)]]]]
;    (c/validate model validations) => nil))
;
;
;(facts "if value it lense is nil then mandatory error is returned"
;  (let [validations [[:a (l/in [:a]) [c/mandatory]]]]
;    (c/validate {:a nil} validations) => {:a [{:error :mandatory}]}
;    (c/validate {} validations) => {:a [{:error :mandatory}]}
;    (c/validate {:a 1} validations) => nil))
;
;(facts "validation short-circuits if value not present"
;  (let [validations [[:a (l/in [:a]) [c/optional (fn [v] :fail)]]]]
;    (c/validate {:a nil} validations) => nil
;    (c/validate {:a 1} validations) => {:a [:fail]}))
;
;(facts "returns error if lower than min length"
;  (let [validations [[:a (l/in [:a]) [(c/min-length 4)]]]]
;    (c/validate {:a "123"} validations) => {:a [{:error :min-length
;                                                 :value "123"
;                                                 :constraint 4}]}
;    (c/validate {:a "1234"} validations) => nil))
;
;(facts "returns error if max length is exceeded"
;  (let [validations [[:a (l/in [:a]) [(c/max-length 4)]]]]
;    (c/validate {:a "12345"} validations) => {:a [{:error :max-length
;                                                   :value "12345"
;                                                   :constraint 4}]}
;    (c/validate {:a "1234"} validations) => nil))
;
;(facts "returns error if exact length is not matched"
;       (let [validations [[:a (l/in [:a]) [(c/exact-length 4)]]]]
;         (c/validate {:a "12345"} validations) => {:a [{:error :exact-length
;                                                        :value "12345"
;                                                        :constraint 4}]}
;         (c/validate {:a "123"} validations) => {:a [{:error :exact-length
;                                                      :value "123"
;                                                      :constraint 4}]}
;         (c/validate {:a "1234"} validations) => nil))
;
;(facts "returns error if value is not a string"
;       (let [validations [[:a (l/in [:a]) [c/is-string]]]]
;         (c/validate {:a 1} validations) => {:a [{:error :string :value 1}]}
;         (c/validate {:a "bob"} validations) => nil))
;
;(facts "returns error if value is not a number"
;       (let [validations [[:a (l/in [:a]) [c/is-number]]]]
;         (c/validate {:a "2"} validations) => {:a [{:error :number :value "2"}]}
;         (c/validate {:a 2} validations) => nil))
;
;(facts "greater-than"
;       (let [validations [[:a (l/in [:a]) [(c/greater-than 1)]]]]
;         (c/validate {:a 1} validations) => {:a [(c/error-map :greater-than 1 1)]}
;         (c/validate {:a 2} validations) => nil))
;
;(facts "greater-than-or-equal-to"
;       (let [validations [[:a (l/in [:a]) [(c/greater-than-or-equal-to 2)]]]]
;         (c/validate {:a 1} validations) => {:a [(c/error-map :greater-than-or-equal-to 1 2)]}
;         (c/validate {:a 2} validations) => nil
;         (c/validate {:a 3} validations) => nil))
;
;(facts "less-than"
;       (let [validations [[:a (l/in [:a]) [(c/less-than 2)]]]]
;         (c/validate {:a 2} validations) => {:a [(c/error-map :less-than 2 2)]}
;         (c/validate {:a 1} validations) => nil))
