"""
raccoon_llm.py — builds the chat model, whichever provider you picked.

One function, get_llm(). Everything else in the project calls it and never
knows or cares which provider is behind it.

Bedrock speaks its own protocol, so it uses langchain-aws. Everything else
(NVIDIA, OpenAI, OpenRouter, Ollama, any custom server) speaks the OpenAI
chat-completions protocol, so they all share one client with a different
base_url. That is why adding a provider is a two-line change in
raccoon_config.py rather than a new code path here.

IMPORTANT: the agent works by calling tools, so whichever model you choose
MUST support function/tool calling. Most current instruct models do;
small ones under about 7B often do not, or do it badly. If the agent runs
but never calls a tool and just describes what it would do, that is almost
always the cause.
"""

from __future__ import annotations

import raccoon_config as cfg


class LLMSetupError(RuntimeError):
    """Raised with an explanation a human can act on."""


def get_llm(temperature: float = 0.4):
    """Returns a LangChain chat model for the configured provider."""
    provider = cfg.LLM_PROVIDER

    if provider == "bedrock":
        try:
            from langchain_aws import ChatBedrockConverse
        except ImportError as exc:
            raise LLMSetupError(
                "langchain-aws is not installed. Run:\n"
                "    pip install -r requirements.txt"
            ) from exc

        if not cfg.AWS_ACCESS_KEY_ID:
            raise LLMSetupError(
                "LLM_PROVIDER=bedrock but AWS_ACCESS_KEY_ID is not in your "
                "keys file.\nIf you don't have working AWS access yet, switch "
                "to the free option:\n    LLM_PROVIDER=nvidia"
            )
        return ChatBedrockConverse(
            model=cfg.LLM_MODEL,
            region_name=cfg.AWS_REGION,
            temperature=temperature,
        )

    # --- everything else is OpenAI-compatible -----------------------------
    try:
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise LLMSetupError(
            "langchain-openai is not installed. Run:\n"
            "    pip install -r requirements.txt"
        ) from exc

    if not cfg.LLM_BASE_URL:
        raise LLMSetupError(
            f"LLM_PROVIDER={provider} has no endpoint. Set LLM_BASE_URL in "
            f"your keys file."
        )
    if not cfg.LLM_MODEL:
        raise LLMSetupError(
            f"LLM_PROVIDER={provider} has no model. Set LLM_MODEL in your "
            f"keys file."
        )

    key = cfg.llm_api_key()
    if not key:
        raise LLMSetupError(
            f"LLM_PROVIDER={provider} needs {cfg.llm_key_name()} in your keys "
            f"file.\n"
            + ("Get a free one at build.nvidia.com (no credit card).\n"
               if provider == "nvidia" else "")
        )

    return ChatOpenAI(
        model=cfg.LLM_MODEL,
        base_url=cfg.LLM_BASE_URL,
        api_key=key,
        temperature=temperature,
        timeout=180,          # some hosted open models are slow to first token
        max_retries=2,
    )


def check() -> str:
    """One cheap round trip. Returns the model's reply, or raises."""
    from langchain_core.messages import HumanMessage

    llm = get_llm(temperature=0)
    reply = llm.invoke([HumanMessage(content="Reply with exactly: ready")])
    return (reply.content if isinstance(reply.content, str)
            else str(reply.content)).strip()


def check_tools() -> str:
    """Confirms the model can actually CALL a tool, not just talk about one.

    This is the check that matters. A model that chats fine but can't emit
    tool calls will produce an agent that narrates instead of building.
    """
    from langchain_core.messages import HumanMessage
    from langchain_core.tools import tool

    @tool
    def add_numbers(a: int, b: int) -> int:
        """Add two numbers together."""
        return a + b

    llm = get_llm(temperature=0).bind_tools([add_numbers])
    reply = llm.invoke([HumanMessage(
        content="Use the add_numbers tool to add 17 and 25. Call the tool.")])

    calls = getattr(reply, "tool_calls", None)
    if not calls:
        raise LLMSetupError(
            f"Model '{cfg.LLM_MODEL}' answered but did not call the tool.\n"
            f"The agent is built entirely on tool calls, so it will not work "
            f"with this model.\nPick a model whose card lists function or tool "
            f"calling support."
        )
    return f"called {calls[0]['name']}({calls[0]['args']})"


if __name__ == "__main__":
    print("Configuration:\n")
    print(cfg.describe())
    print()
    try:
        print("chat:  " + check())
        print("tools: " + check_tools())
        print("\nWorking.")
    except Exception as exc:  # noqa: BLE001
        print(f"\nFAILED: {exc}")
