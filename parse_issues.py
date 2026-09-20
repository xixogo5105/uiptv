import json

with open('/Users/younas/.local/share/kilo/tool-output/tool_0bdd1b225001ZmrhsErw7YHBjb') as f:
    data = json.load(f)

issues = data['issues']
with open('/Volumes/backup/code/uiptv/issues_output.txt', 'w') as out:
    out.write(f"Total issues: {len(issues)}\n\n")
    for i, issue in enumerate(issues):
        out.write(f"Issue {i+1}:\n")
        out.write(f"  RULE_KEY: {issue['rule']}\n")
        out.write(f"  SEVERITY: {issue['severity']}\n")
        out.write(f"  COMPONENT: {issue['component']}\n")
        out.write(f"  LINE: {issue.get('line', 'N/A')}\n")
        out.write(f"  MESSAGE: {issue['message']}\n")
        out.write(f"  TYPE: {issue['type']}\n")
        out.write("\n")