import stylelint from 'stylelint';

const {
  createPlugin,
  utils: { report, ruleMessages },
} = stylelint;

const ruleName = 'gameroom/hover-needs-fine-pointer';
const messages = ruleMessages(ruleName, {
  rejected: (selector) =>
    `"${selector}" uses :hover outside @media (hover: hover) and (pointer: fine). ` +
    'On touch screens a tapped element keeps its hover style until something else is tapped.',
});
const HOVER_MEDIA = /\bhover\s*:\s*hover\b/;

function insideHoverMedia(node) {
  for (let parent = node.parent; parent; parent = parent.parent) {
    if (parent.type === 'atrule' && parent.name === 'media' && HOVER_MEDIA.test(parent.params)) {
      return true;
    }
  }
  return false;
}

/** Every rule whose selector has :hover must sit inside a hover-capable @media block. */
const rule = (enabled) => (root, result) => {
  if (!enabled) return;
  root.walkRules((node) => {
    if (node.selector.includes(':hover') && !insideHoverMedia(node)) {
      report({ result, ruleName, node, message: messages.rejected(node.selector) });
    }
  });
};

rule.ruleName = ruleName;
rule.messages = messages;

export default createPlugin(ruleName, rule);
