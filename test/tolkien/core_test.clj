(ns tolkien.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [tolkien.core :as tokenizer]))

(deftest token-counters
  (testing "simple token counter"
    (is (= 2
           (tokenizer/count-tokens "gpt-3.5-turbo" "foo bar"))))

  (testing "token counter for chat messages"
    (is (= 15
           (tokenizer/count-chat-completion-tokens
            {:model "gpt-3.5-turbo"
             :messages [{:role "system" :content "foo"}
                        {:role "user" :content "foo"}]}))))

  ;; NOTE: for this prompt and 3.5-turbot we get 79 prompt tokens in OpenAI API response
  ;; so it just happens it's accurate, for more complicated schemas the token counter will
  ;; most likely overcount, rather than undercount
  (testing "token counter with functions and schemas"
    (is (= 79
           (tokenizer/count-chat-completion-tokens
            {:model "gpt-3.5-turbo"
             :messages [{:role "system" :content "You're a helpful assistant."}
                        {:role "user" :content "How many items are in this list? bananas, apples, raspberries"}]

             :functions [{:name :count_items
                          :parameters {:type "object"
                                       :properties {:explanation {:type "string"}
                                                    :result_type {:type "string"
                                                                  :enum ["number" "list"]}
                                                    :result {:type "integer"}}
                                       :required ["explanation" :result_type :result]}}]
             :function_call {:name :count_items}})))))
