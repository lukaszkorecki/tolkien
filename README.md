# Tolkien

## Installation & usage

Add to your `deps.edn`:


```clojure
 io.github.lukaszkorecki/tolkien {:git/tag "v0.2.0" :git/sha "5472597"}
 ```

 And try it out:

 ```clojure
 (require '[tolkien.core :as token])

 ;; Simple token count
 (token/count-tokens "gpt-3.5-turbo" "Hello World!")

 ;; Count chat-completion API request tokens:

 (token/count-chat-completion-tokens {:model "gpt-3.5-turbo"
                                      :messages [{:role "system"
                                                  :content "You're a helpful assistant, but sometimes you make things up."}
                                                 {:role "user"
                                                  :content "How many items are in this list? bananas, apples, raspberries"}]})
 ```

## What is it?

If you're working with [OpenAIs chat completion API](https://platform.openai.com/docs/guides/gpt/chat-completions-api) sooner
or later, you're going to run into the token size limit of the context length limit:

> InvalidRequestError: This model’s maximum context length is 16385 tokens. However, your messages resulted in 18108 tokens. Please reduce the length of the messages.

Tolkien helps you to get accurate token counts for strings and the Chat Completion API.

:warning: Chat Completion API payloads are notoriously hard to get accurate counts for - [read this blog post explaning why](https://medium.com/@lukaszkorecki/tolkien-clojure-library-for-accurate-token-counting-for-openai-apis-cd03b618232).

Based on my experiments, usage of real life data (short, mid-size and long form text), Tolkien has a ~25 token error margin, meaning it
will undercount or overcount by 25 tokens max, given the Chat Completion API features used.

# Credits & acknowledgments

- JTokkit - https://github.com/knuddelsgmbh/jtokkit - as Java implementation of TikToken
- Kind people on OpenAI forums that tried various techniques to get token counts for their prompts: https://community.openai.com/t/how-to-calculate-the-tokens-when-using-function-call/266573/10
