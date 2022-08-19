# Spec-putter

This is a simple program that contains a minimal
[Reitit](https://github.com/metosin/reitit) program that uses
[Spec](https://clojure.org/about/spec) to validate a HTTP PUT request
body, as well as the responses that are produced. This README
documents the journey of producing it.

To be more specific, the goal here is to find a simple setup that
allows using the request parameters as described on the [Pluggable
Coercion](https://cljdoc.org/d/metosin/reitit/0.5.18/doc/ring/pluggable-coercion)
page.

## Study log

## 2022-08-19

Difficult to remember where I was.

Looks like I have somewhat working coercion for the path parameters,
at least.

Finally figured what coerce-exceptions-middleware does and how to
install it.

## Takeaways
### coercion middleware ordering
It makes sense how that I think about it, but one potential stumbling
block is that `coerce-exceptions-middleware` has to be placed before
e.g. `coerce-request-middleware`, or the former will not catch
exceptions thrown in the later one.

## Data model

The motivation for this exercise does not originate from
[Spec](https://clojure.org/about/spec), even though it came up during
familiarization with Spec. The actual impetus comes from problems with
setting up a working Reitit Middleware stack that would use it for
message bodies. Still, some minimal data specification needs to be
available to exercise the stack, so here we have something.

Spec is imported from `clojure.spec.alpha` as `s`, with which we have
these specifications.

```clojure
(s/def ::title string?)
(s/def ::description string?)
(s/def ::version int?)

(s/def ::foo
  (s/keys :req-un [::title ::description ::version]))

(s/def ::foo-update
  (s/keys :req-opt [::title ::description]))

(defonce foo (atom {:title "foo0"
                    :description "The initial description of foo"
                    :version 0}))
```

So basically there is an in-memory database of a single `main/foo`
object that has a few attributes for us.


## HTTP service

Let's proceed by simply setting up a
[http-kit](https://github.com/http-kit/http-kit) based HTTP service.

```clojure
(defonce server (atom nil))

(defn toplevel-handler []
  (fn [req]
    {:body "Hello, World!"}))

(defn run []
  (swap! server (fn [old-server]
                  (when old-server
                    (old-server))
                  (run-server (toplevel-handler) {:port 8081}))))
```

Now calling `run` will just close down the previous server and start a
new one.

This is still quite trivial, but let's add the reitit stuff in there
as well. So now the handler definition with some debug tweaks looks like:

```clojure
(def last-req (atom nil))

(defn hello-name [{{{:keys [name]} :path} :parameters :as req}]
  (reset! last-req req)
  {:body (str "Hello, " name "!\n")})

(defn toplevel-handler []
  (reitit-ring/ring-handler
   (reitit-ring/router
    [["/hello/:name" {:get {:handler hello-name}}]])
   {:reitit.middleware/transform print-request-diffs}))
```

Now if I request localhost:8081/hello/joonas, it seems that the
`:parameters` entry is absent in the request. What is present there is
these:

```clojure
(:reitit.core/match
 :reitit.core/router
 :remote-addr
 :headers
 :async-channel
 :server-port
 :content-length
 :websocket?
 :content-type
 :character-encoding
 :uri
 :server-name
 :query-string
 :path-params
 :body
 :scheme
 :request-method)
```

So `:path-params` is present, but a combined `:parameters` is
absent. This is not too surprising - the coercion is not yet
installed, after all!


