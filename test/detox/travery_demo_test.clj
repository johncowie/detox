(ns detox.travery-demo-test
  (:require [clojure.test :refer :all]
            [detox.core :as c]
            [detox.validators :as v]
            [traversy.lens :as l]))

;; Firstly, let's make a sample data structure that we want to validate for demo purposes


;; Then, let's define some traversy lenses for digging around in the data structure
(def address->line-1 (l/in [:line-1]))
(def address->line-2 (l/in [:line-2]))
(def address->town (l/in [:town]))
(def address->postcode (l/in [:postcode]))

;; Now we make some validators that we'll reuse in a few different places
(defn string-of-length [lower-limit upper-limit]
  (c/chain v/is-string (v/length-greater-than lower-limit) (v/length-less-than upper-limit)))

;; Ok let's make a validator for validating an address - note that we'll make address-line-2 and town optional
(def address-validator
  (c/group
    (c/at (c/chain v/mandatory (string-of-length 5 100)) :address-line-1 address->line-1)
    (c/at (c/chain v/optional (string-of-length 5 100)) :address-line-2 address->line-2)
    (c/at (c/chain v/optional (string-of-length 5 50)) :town address->town)
    (c/at (c/chain v/mandatory (string-of-length 5 10)) :postcode address->postcode)))

;; Cool, let's give that a whirl.

;; Ok let's write a validator that covers the whole datastructure
;;  Note that we can now reuse our address-validator in a few different places



;; And let's demonstrate how it works with some tests!






;; So now you're thinking 'these error maps are all well and good,
;;   but what about some translating these into some friendlier messages?
;;
;; Well, you could write something or yourself, or you can use the provided translation stuff



;; This is all well and good, but how do you know what translations to write?
;;  - You can find out all the possible errors that can come out of your validator!


;; As a result, you can use the *** function to check whether you are missing any translations
;;  (you can make a call to this in your test code)








