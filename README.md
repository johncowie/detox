# Detox

Detox is my attempt at a validation library, to scratch a particular itch that I have.  The approach taken is a little
different to most of the existing Clojure validation libraries, with a strong emphasis on composing new validators out of smaller validators.  I was also interested in creating a validation library that allowed some decoupling from the validation rules and the structure of the data.

### How do I get it?

Add the following to your project dependencies:
```clojure
  [detox "0.0.1"]
```

### OK, how do I use it?

Let's validate that a value exists.  

```clojure
  (:require
    [detox.core :as c]
    [detox.validators :as v])

  (c/validate 1 v/mandatory)
  ;; => {:result :success :value 1}

  (c/validate nil v/mandatory)
  ;; => {:result :error
  ;;     :value [{:type [:mandatory] :value nil :constraints {}}]}

```
`mandatory` is a 'value validator'.  We can make more complex validators by composing these value validators together.

Let's say that we want to validate that a value exists, and then check that it is greater than 3. We can `chain` these validators, so that an error will be returned for the first validator that fails:

```clojure
(def >3 (c/chain v/mandatory (v/greater-than 3)))

(c/validate nil >3)
;; => {:result :error
;;     :value [{:type [:mandatory] :value nil :constraints {}}]}  

(c/validate 1 >3)
;; => {:result :error
;;     :value [{:type [:greater-than] :value 1 :constraints {:limit 3}}]}

(c/validate 4 >3)
;; => {:result :success
;;     :value 4}
```

Ok so obviously we're also going to want to validate more complex data structures than
single values.  We can use the ```at``` function to point validators at values nested in our
data structures. For example let's say that we want to verify that age is greater than 3:

```clojure
(def age>3 (v/at >3 :age-check [:age])) ;; :age-check is an identifier that will be added to errors

(validate {:age 4} age>3)
;; => {:result :success :value 4}

(validate {:age 2} age>3)
;; => {:result :error
;;     :value [{:type [:age-check :greater-than] :value 2 :constraints {:limit 3}}]}

(validate {} age>3)
;; => {:result :error
;;     :value [{:type [:age-check :mandatory] :value nil :constraints {}}]}
```

Ok you're probably also wanting to `group` validators together. For example we want to check that
a name exists, and that the age is greater than 3.

```clojure

(def name-present? (v/at v/mandatory :name-check [:name]))

(def person-validator (v/group name-present? age>3))

(validate {:name "Bob" :age 4} person-validator)
;; {:result :success
;;  :value {:name "Bob" :age 4}}

(validate {} person-validator)
;; {:result :error
;;  :value [{:type [:name-check :mandatory] :value nil :constraints {}}
;;          {:type [:age-check :mandatory] :value nil :constraints {}}]}

(validate {:name nil :age 2} person-validator)
;; {:result :error
;;  :value [{:type [:name-check :mandatory] :value nil :constraints {}}]
;;          {:type [:age-check :greater-than] :value 2 :constraints {:limit 3}}}
```

Using `chain`, `at`, and `group` you can compose your validators to validate increasingly large
data structures with increasingly complex rules.

### How do I make my own validators?

You can make a validator using the `base-validator` function.
The arguments are:
 - an identifier for the validation
 - a function that takes a value and returns a success result or error result
 - a map of parameterised constraints used in the validation.

For example, the `greater-than` validator could be defined as follows:

```clojure
  (defn greater-than [limit]
    (c/base-validator
      :greater-than
      (fn [v] (if (> v limit) (c/success-value v) (c/error-value v)))
      {:limit limit}))
```

The constraints map is not used during validation, but will be returned with the errors. This can be used for understanding the context in which the validation failed (particularly useful for producing helpful error messages).

If that definition looks a bit verbose then are a couple of handy macros for writing the same thing. The above is equivalent to:

```clojure
  (:require
    [detox.macros :refer [defvalidator defpredicate]])

  (defvalidator greater-than [v limit] (if (> v limit) (c/success-value v) (c/error-value v)))
```

which is also equivalent to:

```clojure
  (defpredicate greater-than [v limit] (> v limit))
```

Note: If you only pass one argument to the macro, a validator will be returned instead of a function that takes arguments and returns a validator.

### Can I update values when I'm validating stuff?

Yes, you can! Let's say that you want to parse an integer from a string, you simply need to return the parsed value in the success return type.  These updates will be made available to subsequent validations, and propagated back up your data structures.  For example:

```clojure
  (defvalidator parse-integer [s]
    (try
      (c/success-value (Integer. s))
      (catch Exception e
        (c/error-value s))))

  (c/validate "3" parse-integer)
  ;; => {:result :success :value 3}

  (c/validate "blah" parse-integer)
  ;; => {:result :error :value [{:type [:parse-integer] :value "blah" :constraints {}}]}

  (c/validate "6" (c/chain mandatory parse-integer (greater-than 5)))
  ;; => {:result :success :value 6}

  (c/validate "4" (c/chain mandatory parse-integer (greater-than 5)))
  ;; => {:result :error :value [{:type [:greater-than] :value 4 :constraints {}}]}

  (c/validate {:a {:b "6"}} (c/at parse-integer :a-b [:a :b]))
  ;; => {:result :success :value {:a {:b 6}}}

```

### Right, I got some errors out, can I translate them into error messages?

Yeah as it happens there's some stuff provided for that.  It works out-of-the-box by providing a nested map with template strings.  Words surrounded with ~~ represent keys in the constraints map. ~value~ will template in the value that the validator attempted to validate.

```clojure
  (:require
    [detox.translate :as t])

  (def translations {
    :name-check {
      :mandatory "You need to specify a name."
    }
    :age-check {
      :mandatory "You need to specify an age."
      :greater-than "Age needs to be greater than ~~limit~~, was ~value~."
    }
  })

  (def validator
    (c/group
      (c/at v/mandatory :name-check [:name])
      (c/at (c/chain v/mandatory (v/greater-than 18)) :age-check [:age])))

  (-> {:name nil :age 7} (c/validate validator) (t/translate translations))
  ;; => {:name-check {:mandatory "You need to specify a name."}
  ;;     :age-check {:greater-than "Age needs to be greater than 18, was 7."}}
```

<!-- ### I keep forgetting to add translations for errors when I update my validator... -->

<!-- ### What do I do if I have validations that are dependent on multiple other validations? -->

<!-- ### Ok, I've got one for you, I want to run a single validator in multiple places in my data, can I do that? -->

<!-- ### I just want to lay out my validations in a map like the other clojure libraries - how do I do that? -->

<!-- ### Can I use this with ClojureScript?  -->
<!-- explain why this isn't great - coupling to data structure shape -->
## License

Copyright © 2016 John Cowie

Distributed under the Eclipse Public License, the same as Clojure.
