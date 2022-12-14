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

### 2022-08-19

Difficult to remember where I was.

Looks like I have somewhat working coercion for the path parameters,
at least.

Finally figured what coerce-exceptions-middleware does and how to
install it.

Now to get into request bodies, let's define a handler that simply
echoes the body back to the caller:

```clojure
(defn echo-body [{{:keys [body]} :parameters}]
  {:body body})
```

Now when installed with no type specs, such as in `["/echo-body" {:put
{:handler echo-body}}]`, it seems that the body is not returned to the
caller, further debugging indicates that the body parameter is
actually `nil`, so clearly something needs to create it there. It
seems quite likely that the coercion is expected to produce it.

Let's add a coercion - maybe just tell that it is an `int`, so it now
looks like:

```clojure
["/echo-body" {:put {:handler echo-body
                     :parameters {:body int?}}}]
```


Now this gives an error like this (reformatted for easier reading):

```
[:spec "(spec-tools.core/spec {:spec clojure.core/int?, :type :long, :leaf? true})"]
[:problems [{:path [], :pred "clojure.core/int?", :val nil, :via [], :in []}]]
[:type :reitit.coercion/request-coercion]
[:coercion :spec]
[:value nil]
[:in [:request :body-params]]
```

So looks like it wants to see a `:body-params` in the request, which
is seemingly absent.

By a quick look, at least
[muuntaja](https://cljdoc.org/d/metosin/reitit/0.5.18/api/reitit.ring.middleware.muuntaja)
is something that could create us a `:body-params`.

Interestingly, adding `muuntaja.middleware/wrap-format` into the
middleware chain did not fix this. It looks like `:body-params` is
still missing.

It turns out, that the caller is required to declare the request as
`Content-Type: application/json` or similar, or Muuntaja will just
ignore the body. Maybe it makes sense.

One final thing to fight with was the response coercion, after trying
out all sorts of random stuff, I get the impression that Spec coercion
just does not coerce responses. The
[thread](https://github.com/metosin/reitit/issues/297) here kind of
reinforces that belief.

### 2022-08-20

Did a quick conversion to Schema coercion, in hopes of getting
response coercion working as well. However, it turns out that also
here we are not getting any response coercion. What is happening?

It turns out that `coerce-response-middleware` will merrily ignore
responses that do not have their `:status` set. This is not too
surprising because the response schemas are set for each status code
separately. This however means, that one should always remember to set
the status even for `200 OK` responses, instead of relying that some
outer layer will fill it in.

The earlier revelation - that spec coercion would not work for
responses - turned out to be outright incorrect.

## Takeaways
### coercion exception middleware ordering
It makes sense how that I think about it, but one potential stumbling
block is that `coerce-exceptions-middleware` has to be placed before
e.g. `coerce-request-middleware`, or the former will not catch
exceptions thrown in the later one.

### Interaction between request coercion, muuntaja ja content-type

Request coercion needs:
- `:body-params`, which in turn needs
- Muuntaja (or maybe some other tool) which needs
- `Content-Type` header in the request

### Always set response status explicitly

At least `coerce-response-middleware` expects to see the status.

If one wants to avoid having to set it manually for `200 OK` resposes,
it would be easy to add a middleware that fills it in right after
getting the response from the per-route handler.

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


