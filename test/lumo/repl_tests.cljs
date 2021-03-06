(ns lumo.repl-tests
  (:require [cljs.nodejs :as node]
            [cljs.test :refer [deftest is testing use-fixtures]]
            [lumo.repl :as lumo]
            [lumo.common :as common]
            [lumo.test-util :as test-util]))

(use-fixtures :once test-util/with-parinfer test-util/with-cache)

(deftest test-is-readable?
  (is (false? (lumo/is-readable? "(")))
  (is (= (lumo/is-readable? "(+ 1 2)") ""))
  (is (= (lumo/is-readable? "(+ 1 2) :foo") " :foo"))
  (is (= (lumo/is-readable? "") ""))
  (is (= (lumo/is-readable? ")") "")))

(deftest indent-space-count-test
  (is (= (lumo/indent-space-count "(let [") 6))
  (is (= (lumo/indent-space-count "(do") 1))
  (is (= (lumo/indent-space-count "(let [a 1") 6)))

(deftest test-form-start
  (is (= (lumo/form-start "( )" 2) 0))
  (is (= (lumo/form-start "(let [a 1\nb 2])" 13) 5))
  (is (= (lumo/form-start "#{1 2 3}" 7) 1))
  (is (= (lumo/form-start "(+ 1 2" 5) nil))
  (is (= (lumo/form-start "(let [a 1\n      b 2\n      c 3]" 29) 5))
  (is (= (lumo/form-start "(())" 3) 0)))

(deftest test-get-highlight-coords
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(+ 1 2)"] 6)) [0 0]))
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(+ 1 2"] 5)) [-1 -1]))
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(+ 1 2" ")"] 0)) [0 1]))
  (is (= (js->clj (lumo/get-highlight-coordinates
                    #js ["(let [a 1" "      b 2" "      c 3]"] 9))
         [5 2]))
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(let [a 1)"] 9)) [-1 -1]))
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(let [a 1])"] 9)) [5 0]))
  (is (= (js->clj (lumo/get-highlight-coordinates
                    #js ["(let [a 1" "" "      b 2" "      c 3]"] 9))
         [5 3]))
  (is (= (js->clj (lumo/get-highlight-coordinates #js ["(())"] 3)) [0 0])))

(deftest test-get-completions
  (testing "keyword completions"
    (is (= (js->clj (lumo/get-completions ":")) (sort (into [] (map str) lumo/keyword-completions))))
    (is (= (js->clj (lumo/get-completions ":a")) [":args" ":as"]))
    (is (= (js->clj (lumo/get-completions ":ref")) [":refer" ":refer-clojure" ":refer-macros"])))
  (testing "aliased namespaces completions"
    (with-redefs [lumo/current-alias-map (fn []
                                           '{string clojure.string})]
      (is (contains? (set (lumo/get-completions "str")) "string/"))
      (is (contains? (set (lumo/get-completions "(str")) "(string/"))
      (is (not (contains? (set (lumo/get-completions "(set")) "(set/"))))
    (with-redefs [lumo/all-ns (fn [] '(clojure.set clojure.string))]
      (is (contains? (set (lumo/get-completions "(clojure.s")) "(clojure.set"))))
  (testing "cljs.core function completions"
    (is (contains? (set (lumo/get-completions "sub")) "subs"))
    (is (contains? (set (lumo/get-completions "mer")) "merge")))
  (testing "referred vars completions"
    (with-redefs [lumo/get-namespace (fn [_]
                                       '{:uses {foo foo.core}
                                         :requires {foo.core foo.core}
                                         :use-macros {longer-var bar.core}})]
      (is (contains? (set (lumo/get-completions "fo")) "foo"))
      (is (contains? (set (lumo/get-completions "lon")) "longer-var"))
      (is (contains? (set (lumo/get-completions "(lon")) "(longer-var"))))
  (testing "LUMO-83"
    (is (contains? (set (lumo/get-completions "clojure.core/")) "clojure.core/merge"))
    (is (contains? (set (lumo/get-completions "")) "merge"))))

(deftest test-root-resource
  (is (= (lumo/root-resource 'foo-bar-baz) "/foo_bar_baz"))
  (is (= (lumo/root-resource 'foo.bar.baz) "/foo/bar/baz")))

(deftest test-get-arglists
  (is (= (lumo/get-arglists "whatever") nil))
  (is (= (lumo/get-arglists "map") '([f] [f coll] [f c1 c2] [f c1 c2 c3] [f c1 c2 c3 & colls])))
  (is (= (lumo/get-arglists "when") '([test & body]))))
