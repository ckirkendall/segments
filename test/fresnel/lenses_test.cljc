(ns fresnel.lenses-test
  (:require-macros [fresnel.lenses :refer [deflens deffetch defputback]])
  (:require #?(:clj [clojure.test :refer [deftest testing is are]]
               :cljs [clojure.test :refer-macros [deftest testing is are]])
            [fresnel.lenses :refer [#?(:clj deflens) #?(:clj deffetch) #?(:clj defputback)
                                    IFetch IPutback fetch putback create-lens slice
                                    key dissoc-trigger]]
            [clojure.string :refer [split]]))

(deflens comma-to-map [oval nval]
  :fetch
  (reduce #(assoc %1 (.trim %2) true) {} (split oval #","))
  :putback
  (reduce #(if %1 (str %2 "," %1) %2)
          nil
          (filter #(nval %1) (keys nval))))

(deftest fetch-tests
  (testing "fetching using simple keyword"
    (let [data {:a "success"}]
      (is (= "success" (fetch data :a)))))
  (testing "fetching using a string"
    (let [data {"a" "success"}]
      (is (= "success" (fetch data "a")))))
  (testing "fetching using a string"
    (let [data {'a "success"}]
      (is (= "success" (fetch data 'a)))))
  (testing "fetching using a number on a vector"
    (let [data ["a" "b" "c"]]
      (is (= "b" (fetch data 1)))))
  (testing "fetching using a number on a list"
    (let [data '("a" "b" "c")]
      (is (= "b" (fetch data 1)))))
  (testing "fetching using a simple custom segment"
    (let [seg (create-lens (fn [_ data] (nth data 1))
                           (fn [_ _ nval] nval))
          data ["a" "b" "c"]]
      (is (= "b" (fetch data seg)))))
  (testing "fetching using a complex segement"
    (let [data "a,b,c"]
      (is (= {"a" true
              "b" true
              "c" true}
             (fetch data comma-to-map)))))
  (testing "fetching using slice segment"
    (let [data [:a :b :c :d :e :f]]
      (is (= '(:c :d)
             (fetch data (slice 2 4)))))))


(deftest putback-tests
  (testing "putback using simple keyword"
    (let [data {:a "fail"}]
      (is (= {:a "success"} (putback data :a "success")))))
  (testing "putback using a string"
    (let [data {"a" "fail"}]
      (is (= {"a" "success"} (putback data "a" "success")))))
  (testing "putback using a string"
    (let [data {'a "fail"}]
      (is (= {'a "success"} (putback data 'a "success")))))
  (testing "putback using a number on a vector"
    (let [data ["a" "fail" "c"]]
      (is (= ["a" "b" "c"] (putback data 1 "b")))))
  (testing "putback using a number on a list"
    (let [data '("a" "fail" "c")]
      (is (= '("a" "b" "c") (putback data 1 "b")))))
  (testing "putback using a custom segment"
    (let [seg (create-lens (fn [_ data] (nth data 1))
                           (fn [_ data nval] (assoc data 1 nval)))
          data ["a" "fail" "c"]]
      (is (= ["a" "b" "c"] (putback data seg "b")))))
  (testing "putback using a complex segement"
    (let [data "a,b,c"
          res (putback data
                       comma-to-map
                       (assoc (fetch data comma-to-map) "d" true))]
      (is (or  (= "a,b,c,d" res)
               (= "d,c,b,a" res)))))
  (testing "fetching using slice segment"
    (let [data [:a :b :c :d :e :f]]
      (is (= '(:a :b :t :t :t :e :f)
             (putback data (slice 2 4) [:t :t :t]))))))


(deftest fetch-in-tests
  (testing "basic compond map with keys"
    (let [data {:a {:b {"c" 1 :d 2} 'e 3} :f [1 2 3 4]}]
      (are [x y]  (= x (fetch data y))
        1 [:a :b "c"]
        3 [:a 'e]
        4 [:f 3]
        {"c" 1 :d 2} [:a :b])))
  (testing "compond map with complex segment"
    (let [data {:a ["x,y,z" "a,b,c"]}]
      (is (= true (fetch data [:a 1 comma-to-map "b"])))
      (is (= nil (fetch data [:a 1 comma-to-map "d"]))))))


(deftest putback-in-tests
  (testing "basic compond map with keys"
    (let [data {:a {:b {"c" 1 :d 2} 'e 3} :f [1 2 3 4]}]
      (is (= {:a {:b {"c" 1 :d 2} 'e 3} :f [1 2 5 4]}
             (putback data [:f 2] 5)))
      (is (= {:a {:b {"c" 1 :d 5} 'e 3} :f [1 2 3 4]}
             (putback data [:a :b :d] 5)))))
  (testing "compond map with complex segment"
    (let [data {:a ["x,y,z" "a,b,c"]}
          res (putback data [:a 1 comma-to-map "b"] false)]
      (is (or (= {:a ["x,y,z" "a,c"]} res)
              (= {:a ["x,y,z" "c,a"]} res))))))


(deffetch fetch-tmp [val]
  val)

(deftest deffetch-tests
  (testing "a deffetch reifies IFetch"
    (is (satisfies? IFetch fetch-tmp))
    (is (= (fetch 3 fetch-tmp) 3))))


(defputback putback-tmp [oval nval]
  (conj oval nval))

(deftest defputback-tests
  (testing "a defputback reifies IFetch"
    (is (satisfies? IPutback putback-tmp))
    (is (= (putback [] putback-tmp 3) [3]))))


(deftest assoc-in-compatibility-test
  (let [state {:a {:b :c}}]
    (testing "basic compatibility with assoc-in and get-in"
      ;; assoc-in
      (is (= {:foo {:bar {:baz 100}}}
             (putback {:foo {:bar {:baz 0}}} [:foo :bar :baz] 100)))
      (is (= {:foo 1 :bar 2 :baz 100}
             (putback {:foo 1 :bar 2 :baz 3} [:baz] 100)))
      (is (= [{:foo [{:bar 2} {:baz 3}]} {:foo [{:bar 2} {:baz 100}]}]
             (putback [{:foo [{:bar 2} {:baz 3}]}, {:foo [{:bar 2} {:baz 3}]}]
                      [1 :foo 1 :baz] 100)))
      (is (= [{:foo 1, :bar 2} {:foo 1, :bar 100}]
             (putback [{:foo 1 :bar 2}, {:foo 1 :bar 2}] [1 :bar] 100)))
      #?(:clj (is (thrown? IndexOutOfBoundsException (putback [] [1 :bar] 100)))
         :cljs (is (thrown? js/Error (putback [] [1 :bar] 100))))
      (is (= {:bar {:foo 100}}
             (putback nil [:bar :foo] 100)))

      ;; get-in
      (is (= 1 (fetch {:foo 1 :bar 2} [:foo])))
      (is (= 2 (fetch {:foo {:bar 2}} [:foo :bar])))
      (is (= 1 (fetch [{:foo 1}, {:foo 2}] [0 :foo])))
      (is (= 4 (fetch [{:foo 1 :bar [{:baz 1}, {:buzz 2}]}, {:foo 3 :bar [{:baz 3}, {:buzz 4}]}]
                      [1 :bar 1 :buzz]))))))


(deftest complex-keys-test
  (testing "making sure the complex key can be created with key function"
    (is (= {:a :b}
           (fetch {[0 1] {:a :b}} [(key [0 1])])))
    (is (= {[0 1] {:a :c}}
           (putback {[0 1] {:a :b}} [(key [0 1]) :a] :c)))
    (is (= {[0 1] {:a :c}}
           (putback {} [(key [0 1]) :a] :c)))))

(deftest dissoc-test
  (testing "making sure that putback with the dissoc-trigger causes a dissoc of the key"
    (is (= {:a :b}
           (putback {:a :b :c :d} :c dissoc-trigger)))))
