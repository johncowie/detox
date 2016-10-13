# Detox

Detox is my attempt at a validation library, to scratch a particular itch that I have.  The approach taken is a little
taken to most of the existing Clojure validation libraries, with a strong emphasis on composing new validators out of smaller validators.  I was also interested in creating a validation library that allowed some decoupling from the validation rules and the structure of the data.

### How do I get it?

Add the following to your project dependencies:
```clojure
  [detox "0.0.1"]
```

### OK, how do I use it?

Let's validate that a value exists.  

```clojure
  (require
    [detox.core :as c]
    [detox.validators :as v])

  (c/validate 1 v/mandatory)
  ;; => {:result :success :value 1}
  (c/validate nil v/mandatory)
  ;; => {:result :error
  ;;     :value [{:type [:mandatory] :value nil :constraints {}}]}

```
`mandatory` is a value validator.  We can make more complex validators by composing these value validators together.

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

<!-- ### How do I make my own validators? -->
<!-- ### Right, I got some errors out, how do I translate them into error messages? -->
<!-- ### I keep forgetting to add translations for errors when I update my validator... -->
<!-- ### What do I do if I have validations that are dependent on multiple other validations? -->
<!-- ### Can I parse values when I'm validating stuff? -->
<!-- ### Ok, I've got one for you, I want to run a single validator in multiple places in my data, can I do that? -->
<!-- ### I just want to lay out my validations in a map like the other clojure libraries - how do I do that? -->
 <!-- // explain why this isn't great - coupling to data strucure shape -->
## License

Copyright © 2016 John Cowie

Distributed under the Eclipse Public License, the same as Clojure.
