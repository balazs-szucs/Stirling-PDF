import { Center, Stack, Loader, Text } from "@mantine/core";

export default function ToolLoadingFallback({
  toolName,
  label,
}: {
  toolName?: string;
  label?: string;
}) {
  return (
    <Center h="100%" w="100%">
      <Stack align="center" gap="md">
        <Loader size="lg" />
        <Text c="dimmed" size="sm">
          {label ?? (toolName ? `Loading ${toolName}...` : "Loading tool...")}
        </Text>
      </Stack>
    </Center>
  );
}
