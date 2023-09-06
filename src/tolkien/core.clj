(ns tolkien.core
  (:require
   [clojure.walk :as walk]
   [clojure.data.json :as json]
   [clojure.string :as str])
  (:import
   (com.knuddels.jtokkit Encodings)
   (com.knuddels.jtokkit.api Encoding EncodingRegistry ModelType)))

(def registry
  ^EncodingRegistry (Encodings/newDefaultEncodingRegistry))

(defn model->encoding [model-name]
  (let [^ModelType model (.orElseThrow (ModelType/fromName model-name))]
    (.getEncodingForModel ^EncodingRegistry registry model)))

(def default-model (model->encoding "gpt-3.5-turbo"))

(defn count-tokens
  "Count tokens for a given model and text"
  [model text]
  (let [encoding ^Encoding (model->encoding model)]
    (.countTokens encoding ^String text)))

(defn- per-message-by-model [model]
  (cond
    (str/starts-with? model "gpt-4")  3
    (str/starts-with? model "gpt-3.5-turbo") 4
    :else (throw (ex-info "Unsupported model" {:model model}))))

(defn count-function-tokens
  "Perform token count on all function names and their schemas.
  Supports:
  - enums
  - arrays of objects
  - objects themselves (maps)
  - primitives
  NOTE:
  This is best effort support, there's some shenaningans going on with the json schema
  as it's being turned into a Typescript code under the hood once it reaches OpenAI API
  so we're mostly guessing here, see more here:
  https://community.openai.com/t/how-to-calculate-the-tokens-when-using-function-call/266573/10
  and https://hmarr.com/blog/counting-openai-tokens/
  for all intents and purposes, this should still work regardless because.
  Since we're doing all of this... it's safer to overcount than undercount.
  The implementation is based on several code snippets but is most likely the most robust
  because it deals with nested schemas, arrays of objects and stuff likethat. All implementations
  or OpenAI's own sample code assume very simple scenarios.
  See here:
  https://github.com/openai/openai-cookbook/blob/main/examples/How_to_count_tokens_with_tiktoken.ipynb
  "
  [encoding functions]
  (let [magic-pad 11
        ;; uh oh, mutable state
        ;; we could use something else here rather than an atom, but let's be honest
        ;; any perf gains are nothing compared to the time cost of the API call ¯\(°_o)/¯
        total (atom magic-pad)]
    ;; first count names and descriptions
    (mapv (fn [f]
            (swap! total +
                   (.countTokens ^Encoding encoding ^String (name (:name f)))
                   (if-let [description (:description f)]
                     (.countTokens ^Encoding encoding ^String description)
                     0)))
          functions)

    ;; then count tokens of all JSON schema elements
    ;; we don't support the whole JSON Schema language, for practical reasons
    ;; but we care about:
    ;; - enums
    ;; - primitive types (e.g. string, integer)
    ;; - arrays
    ;; - objects
    ;; It seems to boild down to counting names and types of things
    (walk/prewalk (fn [item]
                    (if (and
                         (map? item)
                         (:type item))
                      (let [{:keys [type properties description enum required]} item]
                        (swap! total +
                               ;; count type
                               (+ 2 (.countTokens ^Encoding encoding ^String (name type)))
                               ;; add description
                               (if description
                                 (+ 2 (.countTokens ^Encoding encoding ^String description))
                                 0)
                               ;; required attributes list
                               ;; NOTE: this might be overcounting?
                               (if (seq required)
                                 (reduce +
                                         (map (fn [f]
                                                (.countTokens ^Encoding encoding ^String (name f)))
                                              required))
                                 0)
                               ;; count property names
                               (if properties
                                 (reduce +
                                         (map (fn [p]
                                                (.countTokens ^Encoding encoding ^String (name p)))
                                              (keys properties)))
                                 0)
                               ;; enums, simple array of things
                               (if (seq enum)
                                 (reduce + (map (fn [e]
                                                  (.countTokens ^Encoding encoding ^String (name e)))
                                                enum))
                                 0))
                        item)
                      item))
                  functions)
    @total))

(defn count-chat-completion-tokens
  "Given the model and standard messages+functions+function-call payload
  figure out the number of tokens used in the prompt.
  NOTE: This is most likely going to be an overcount, but it's better than undercount
  if your prompts are sensitive to exceeding the context window length. If undercount happens
  it's usually a difference of ~20 tokens max, at least based on my testing.
  NOTE: Function calls are supported but the parameter schema doesn't include all of the JSON Schema
  features, like refs so it's best effort and supports most of MY usecases.
  Args:
  - model - a string representing the model name e.g. gpt-3.5-turbo-16k
  - payload - the request payload as a map, whatever you would send over to OpenAI API

  If you really need a precise count, see this gist and have fun:
  https://gist.github.com/CGamesPlay/dd4f108f27e2eec145eedf5c717318f5
  "
  [{:keys [model messages functions function_call] :as _payload}]
  (let [model (or model default-model)
        encoding ^Encoding (model->encoding model)
        _ (when-not encoding
            (throw (ex-info "Uknown model" {:model model})))
        tokens-per-message (per-message-by-model model)
        message-sum (reduce (fn [acc {:keys [content role] :as _message}]
                              (+ acc
                                 tokens-per-message
                                 (.countTokens encoding ^String content)
                                 (.countTokens encoding ^String (name role))))
                            0
                            messages)
        function-call-tokens (if function_call
                               (+ 3 ;; magic padding
                                  ;; can be a string or map, very approximate!
                                  (.countTokens encoding (json/write-str function_call)))
                               0)
        function-tokens (if (seq functions)
                          (count-function-tokens encoding functions)
                          0)]
    (+ message-sum
       3 ;; magic padding
       ;; these two include magic pading
       function-call-tokens
       function-tokens)))
